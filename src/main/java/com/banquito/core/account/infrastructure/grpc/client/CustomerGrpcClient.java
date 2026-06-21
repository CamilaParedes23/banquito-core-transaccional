package com.banquito.core.account.infrastructure.grpc.client;

import com.banquito.core.account.shared.exception.BusinessException;
import com.banquito.core.customer.infrastructure.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class CustomerGrpcClient {
    private static final String ACTIVE_STATUS = "ACTIVO";

    private final String host;
    private final int port;
    private final long timeoutMs;
    private ManagedChannel channel;
    private CustomerQueryServiceGrpc.CustomerQueryServiceBlockingStub stub;

    public CustomerGrpcClient(@Value("${banquito.integration.customer-grpc-host:core-customer-service}") String host,
                              @Value("${banquito.integration.customer-grpc-port:9092}") int port,
                              @Value("${banquito.integration.grpc-timeout-ms:10000}") long timeoutMs) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        stub = CustomerQueryServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    public CustomerBasicGrpcResponse getByUuid(String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        try {
            return withDeadline().getCustomerByUuid(GetCustomerByUuidRequest.newBuilder()
                    .setCustomerUuid(normalizedUuid)
                    .build());
        } catch (StatusRuntimeException e) {
            throw translate("CUSTOMER_GRPC_ERROR", "No fue posible consultar el cliente por gRPC", e);
        }
    }

    public void validateActive(String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        try {
            var r = withDeadline().validateCustomerStatus(ValidateCustomerStatusRequest.newBuilder()
                    .setCustomerUuid(normalizedUuid)
                    .build());
            if (!r.getValid()) {
                throw new BusinessException(r.getCode(), r.getMessage(), HttpStatus.CONFLICT);
            }
        } catch (StatusRuntimeException e) {
            throw translate("CUSTOMER_GRPC_VALIDATION_ERROR", "No fue posible validar el cliente por gRPC", e);
        }
    }

    /**
     * Validación defensiva para consultas por UUID desde Account.
     * Usa GetCustomerByUuid y valida el estado con la respuesta obtenida.
     * Esto evita bloquear el listado de cuentas por una respuesta negativa del RPC de validación
     * cuando el cliente sí existe y está activo, manteniendo la validación contra Customer.
     */
    public void validateExistsAndActiveForQuery(String uuid) {
        CustomerBasicGrpcResponse customer = getByUuid(uuid);
        if (!ACTIVE_STATUS.equalsIgnoreCase(customer.getStatus())) {
            throw new BusinessException("CUSTOMER_INACTIVE", "Cliente no se encuentra activo", HttpStatus.CONFLICT);
        }
    }

    public void validateMassPaymentsEnabled(String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        try {
            var r = withDeadline().validateMassPaymentsEnabled(ValidateMassPaymentsEnabledRequest.newBuilder()
                    .setCustomerUuid(normalizedUuid)
                    .build());
            if (!r.getValid()) {
                throw new BusinessException(r.getCode(), r.getMessage(), HttpStatus.CONFLICT);
            }
        } catch (StatusRuntimeException e) {
            throw translate("CUSTOMER_GRPC_MASS_PAYMENTS_ERROR", "No fue posible validar pagos masivos por gRPC", e);
        }
    }

    private CustomerQueryServiceGrpc.CustomerQueryServiceBlockingStub withDeadline() {
        return stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private static String normalizeUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            throw new BusinessException("CUSTOMER_UUID_REQUIRED", "El UUID del cliente es obligatorio", HttpStatus.BAD_REQUEST);
        }
        return uuid.trim();
    }

    private BusinessException translate(String code, String fallback, StatusRuntimeException e) {
        String desc = e.getStatus().getDescription();
        if (desc != null && desc.contains("|")) {
            String[] parts = desc.split("\\|", 2);
            return new BusinessException(parts[0], parts[1], HttpStatus.CONFLICT);
        }
        return new BusinessException(code, fallback + ": " + (desc == null ? e.getStatus().getCode() : desc), HttpStatus.CONFLICT);
    }
}
