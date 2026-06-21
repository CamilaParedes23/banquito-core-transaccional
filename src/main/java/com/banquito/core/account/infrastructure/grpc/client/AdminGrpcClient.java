package com.banquito.core.account.infrastructure.grpc.client;

import com.banquito.core.account.shared.exception.BusinessException;
import com.banquito.core.admin.infrastructure.grpc.generated.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Component
public class AdminGrpcClient {
    private final String host; private final int port; private final long timeoutMs; private ManagedChannel channel; private AdminCatalogServiceGrpc.AdminCatalogServiceBlockingStub stub;
    public AdminGrpcClient(@Value("${banquito.integration.admin-grpc-host:core-admin-service}") String host,
                           @Value("${banquito.integration.admin-grpc-port:9093}") int port,
                           @Value("${banquito.integration.grpc-timeout-ms:10000}") long timeoutMs) { this.host = host; this.port = port; this.timeoutMs = timeoutMs; }
    @PostConstruct public void init() { channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build(); stub = AdminCatalogServiceGrpc.newBlockingStub(channel); }
    @PreDestroy public void shutdown() { if (channel != null) channel.shutdown(); }
    public void validateBranchActive(String code) { var r = call(() -> withDeadline().getBranchByCode(BranchCodeRequest.newBuilder().setCode(code).build()), "ADMIN_GRPC_BRANCH_ERROR"); if (!"ACTIVA".equals(r.getStatus())) throw new BusinessException("ADMIN_BRANCH_NOT_ACTIVE", "La sucursal no está activa", HttpStatus.CONFLICT); }
    public AccountSubtypeResponse getActiveAccountSubtype(String code) {
        var response = call(() -> withDeadline().getAccountSubtype(
                AccountSubtypeCodeRequest.newBuilder().setCode(code).build()),
                "ADMIN_GRPC_ACCOUNT_SUBTYPE_ERROR");
        if (!"ACTIVO".equals(response.getStatus())) {
            throw new BusinessException("ADMIN_ACCOUNT_SUBTYPE_NOT_ACTIVE", "El subtipo de cuenta no está activo", HttpStatus.CONFLICT);
        }
        return response;
    }
    public void validateAccountSubtypeActive(String code) { getActiveAccountSubtype(code); }
    public TransactionSubtypeResponse getTransactionSubtype(String code) {
        return call(() -> withDeadline().getTransactionSubtype(
                TransactionSubtypeCodeRequest.newBuilder().setCode(code).build()),
                "ADMIN_GRPC_TRANSACTION_SUBTYPE_ERROR");
    }
    public void validateTransactionSubtypeActive(String code, String expectedMovementType) {
        var r = getTransactionSubtype(code);
        if (!"ACTIVO".equals(r.getStatus())) {
            throw new BusinessException("ADMIN_TRANSACTION_SUBTYPE_NOT_ACTIVE", "El subtipo de transacción no está activo", HttpStatus.CONFLICT);
        }
        if (expectedMovementType != null && !expectedMovementType.equals(r.getBaseMovementType())) {
            throw new BusinessException("ADMIN_TRANSACTION_SUBTYPE_MOVEMENT_MISMATCH", "El subtipo de transacción no corresponde al tipo de movimiento esperado", HttpStatus.CONFLICT);
        }
    }
    public String getTransactionSubtypeName(String code) {
        try {
            var response = getTransactionSubtype(code);
            return response.getName().isBlank() ? code : response.getName();
        } catch (BusinessException exception) {
            return code;
        }
    }
    public FinancialInstitutionResponse getActiveInstitution(String routingCode) {
        var response = call(() -> withDeadline().getInstitutionByRoutingCode(
                        RoutingCodeRequest.newBuilder().setRoutingCode(routingCode).build()),
                "ADMIN_GRPC_INSTITUTION_ERROR");
        if (!"ACTIVA".equals(response.getStatus())) {
            throw new BusinessException(
                    "ADMIN_INSTITUTION_NOT_ACTIVE",
                    "La institución financiera no está activa",
                    HttpStatus.CONFLICT);
        }
        return response;
    }

    public void validateRoutingCode(String routingCode, boolean expectedBanquito) {
        FinancialInstitutionResponse institution = getActiveInstitution(routingCode);
        if (institution.getBanquito() != expectedBanquito) {
            throw new BusinessException(
                    "ACCOUNT_ROUTING_DESTINATION_MISMATCH",
                    expectedBanquito
                            ? "El routing code no corresponde a Banco BanQuito para una instrucción On-Us"
                            : "El routing code corresponde a Banco BanQuito y no puede usarse como destino Off-Us",
                    HttpStatus.CONFLICT);
        }
    }

    public BigDecimal getIvaRate() { var r = call(() -> withDeadline().getIvaRate(EmptyRequest.newBuilder().build()), "ADMIN_GRPC_IVA_ERROR"); return new BigDecimal(r.getValue()); }
    private <T> T call(GrpcCall<T> call, String code) { try { return call.execute(); } catch (StatusRuntimeException e) { throw translate(code, "Error de catálogo administrativo vía gRPC", e); } }
    private AdminCatalogServiceGrpc.AdminCatalogServiceBlockingStub withDeadline() { return stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS); }
    private BusinessException translate(String code, String fallback, StatusRuntimeException e) { String desc = e.getStatus().getDescription(); if (desc != null && desc.contains("|")) { String[] parts = desc.split("\\|",2); return new BusinessException(parts[0], parts[1], HttpStatus.CONFLICT); } return new BusinessException(code, fallback + ": " + (desc == null ? e.getStatus().getCode() : desc), HttpStatus.CONFLICT); }
    private interface GrpcCall<T> { T execute(); }
}
