package com.banquito.core.account.infrastructure.grpc.client;

import com.banquito.core.account.shared.exception.BusinessException;
import com.banquito.core.accounting.infrastructure.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class AccountingGrpcClient {
    private final String host;
    private final int port;
    private final long timeoutMs;
    private final ObjectMapper objectMapper;
    private ManagedChannel channel;
    private AccountingServiceGrpc.AccountingServiceBlockingStub stub;

    public AccountingGrpcClient(
            @Value("${banquito.integration.accounting-grpc-host:core-accounting-service}") String host,
            @Value("${banquito.integration.accounting-grpc-port:9094}") int port,
            @Value("${banquito.integration.grpc-timeout-ms:10000}") long timeoutMs,
            ObjectMapper objectMapper) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        stub = AccountingServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) channel.shutdown();
    }

    public LocalDate getCurrentAccountingDate() {
        try {
            var r = withDeadline().getCurrentAccountingDate(CurrentAccountingDateRequest.newBuilder().build());
            return LocalDate.parse(r.getAccountingDate());
        } catch (StatusRuntimeException e) {
            throw translate("ACCOUNTING_GRPC_DATE_ERROR", "No fue posible obtener la fecha contable por gRPC", e);
        }
    }

    public LocalDate resolveOperationAccountingDate() {
        try {
            var r = withDeadline().resolveOperationAccountingDate(CurrentAccountingDateRequest.newBuilder().build());
            return LocalDate.parse(r.getAccountingDate());
        } catch (StatusRuntimeException e) {
            throw translate("ACCOUNTING_GRPC_OPERATION_DATE_ERROR", "No fue posible resolver la fecha contable operativa por gRPC", e);
        }
    }

    public String createJournalEntry(Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            var request = CreateJournalEntryRequest.newBuilder()
                    .setCorrelationId(String.valueOf(payload.getOrDefault("correlationId", "")))
                    .setPayloadJson(json)
                    .build();
            var r = withDeadline().createJournalEntry(request);
            return r.getJournalEntryUuid();
        } catch (StatusRuntimeException e) {
            throw translate("ACCOUNTING_GRPC_JOURNAL_ERROR", "No fue posible registrar el asiento contable por gRPC", e);
        } catch (Exception e) {
            throw new BusinessException("ACCOUNTING_GRPC_PAYLOAD_ERROR", "No fue posible construir el payload contable: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Optional<String> findJournalEntryByTransactionUuid(String transactionUuid) {
        if (transactionUuid == null || transactionUuid.isBlank()) {
            return Optional.empty();
        }
        try {
            var response = withDeadline().getJournalEntryByTransactionUuid(
                    JournalEntryByTransactionRequest.newBuilder()
                            .setTransactionUuid(transactionUuid.trim())
                            .build());
            return response.getFound() ? Optional.of(response.getJournalEntryUuid()) : Optional.empty();
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw translate("ACCOUNTING_GRPC_JOURNAL_QUERY_ERROR", "No fue posible consultar el asiento contable por gRPC", e);
        }
    }

    private AccountingServiceGrpc.AccountingServiceBlockingStub withDeadline() {
        return stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private BusinessException translate(String code, String fallback, StatusRuntimeException e) {
        String desc = e.getStatus().getDescription();
        if (desc != null && desc.contains("|")) {
            String[] parts = desc.split("\\|", 2);
            return new BusinessException(parts[0], parts[1], HttpStatus.CONFLICT);
        }
        HttpStatus status = switch (e.getStatus().getCode()) {
            case DEADLINE_EXCEEDED, UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.CONFLICT;
        };
        return new BusinessException(code, fallback + ": " + (desc == null ? e.getStatus().getCode() : desc), status);
    }
}
