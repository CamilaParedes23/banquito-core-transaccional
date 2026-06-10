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

@Component
public class CustomerGrpcClient {
    private final String host; private final int port; private ManagedChannel channel; private CustomerQueryServiceGrpc.CustomerQueryServiceBlockingStub stub;
    public CustomerGrpcClient(@Value("${banquito.integration.customer-grpc-host:core-customer-service}") String host,
                              @Value("${banquito.integration.customer-grpc-port:9092}") int port) { this.host = host; this.port = port; }
    @PostConstruct public void init() { channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build(); stub = CustomerQueryServiceGrpc.newBlockingStub(channel); }
    @PreDestroy public void shutdown() { if (channel != null) channel.shutdown(); }
    public CustomerBasicGrpcResponse getByUuid(String uuid) { try { return stub.getCustomerByUuid(GetCustomerByUuidRequest.newBuilder().setCustomerUuid(uuid).build()); } catch (StatusRuntimeException e) { throw translate("CUSTOMER_GRPC_ERROR", "No fue posible consultar el cliente por gRPC", e); } }
    public void validateActive(String uuid) { try { var r = stub.validateCustomerStatus(ValidateCustomerStatusRequest.newBuilder().setCustomerUuid(uuid).build()); if (!r.getValid()) throw new BusinessException(r.getCode(), r.getMessage(), HttpStatus.CONFLICT); } catch (StatusRuntimeException e) { throw translate("CUSTOMER_GRPC_VALIDATION_ERROR", "No fue posible validar el cliente por gRPC", e); } }
    public void validateMassPaymentsEnabled(String uuid) { try { var r = stub.validateMassPaymentsEnabled(ValidateMassPaymentsEnabledRequest.newBuilder().setCustomerUuid(uuid).build()); if (!r.getValid()) throw new BusinessException(r.getCode(), r.getMessage(), HttpStatus.CONFLICT); } catch (StatusRuntimeException e) { throw translate("CUSTOMER_GRPC_MASS_PAYMENTS_ERROR", "No fue posible validar pagos masivos por gRPC", e); } }
    private BusinessException translate(String code, String fallback, StatusRuntimeException e) { String desc = e.getStatus().getDescription(); if (desc != null && desc.contains("|")) { String[] parts = desc.split("\\|",2); return new BusinessException(parts[0], parts[1], HttpStatus.CONFLICT); } return new BusinessException(code, fallback + ": " + (desc == null ? e.getStatus().getCode() : desc), HttpStatus.CONFLICT); }
}
