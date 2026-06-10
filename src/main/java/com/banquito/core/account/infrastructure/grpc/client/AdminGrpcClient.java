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

@Component
public class AdminGrpcClient {
    private final String host; private final int port; private ManagedChannel channel; private AdminCatalogServiceGrpc.AdminCatalogServiceBlockingStub stub;
    public AdminGrpcClient(@Value("${banquito.integration.admin-grpc-host:core-admin-service}") String host,
                           @Value("${banquito.integration.admin-grpc-port:9093}") int port) { this.host = host; this.port = port; }
    @PostConstruct public void init() { channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build(); stub = AdminCatalogServiceGrpc.newBlockingStub(channel); }
    @PreDestroy public void shutdown() { if (channel != null) channel.shutdown(); }
    public void validateBranchActive(String code) { var r = call(() -> stub.getBranchByCode(BranchCodeRequest.newBuilder().setCode(code).build()), "ADMIN_GRPC_BRANCH_ERROR"); if (!"ACTIVA".equals(r.getStatus())) throw new BusinessException("ADMIN_BRANCH_NOT_ACTIVE", "La sucursal no está activa", HttpStatus.CONFLICT); }
    public void validateAccountSubtypeActive(String code) { var r = call(() -> stub.getAccountSubtype(AccountSubtypeCodeRequest.newBuilder().setCode(code).build()), "ADMIN_GRPC_ACCOUNT_SUBTYPE_ERROR"); if (!"ACTIVO".equals(r.getStatus())) throw new BusinessException("ADMIN_ACCOUNT_SUBTYPE_NOT_ACTIVE", "El subtipo de cuenta no está activo", HttpStatus.CONFLICT); }
    public void validateTransactionSubtypeActive(String code, String expectedMovementType) { var r = call(() -> stub.getTransactionSubtype(TransactionSubtypeCodeRequest.newBuilder().setCode(code).build()), "ADMIN_GRPC_TRANSACTION_SUBTYPE_ERROR"); if (!"ACTIVO".equals(r.getStatus())) throw new BusinessException("ADMIN_TRANSACTION_SUBTYPE_NOT_ACTIVE", "El subtipo de transacción no está activo", HttpStatus.CONFLICT); if (expectedMovementType != null && !expectedMovementType.equals(r.getBaseMovementType())) throw new BusinessException("ADMIN_TRANSACTION_SUBTYPE_MOVEMENT_MISMATCH", "El subtipo de transacción no corresponde al tipo de movimiento esperado", HttpStatus.CONFLICT); }
    public BigDecimal getIvaRate() { var r = call(() -> stub.getIvaRate(EmptyRequest.newBuilder().build()), "ADMIN_GRPC_IVA_ERROR"); return new BigDecimal(r.getValue()); }
    private <T> T call(GrpcCall<T> call, String code) { try { return call.execute(); } catch (StatusRuntimeException e) { throw translate(code, "Error de catálogo administrativo vía gRPC", e); } }
    private BusinessException translate(String code, String fallback, StatusRuntimeException e) { String desc = e.getStatus().getDescription(); if (desc != null && desc.contains("|")) { String[] parts = desc.split("\\|",2); return new BusinessException(parts[0], parts[1], HttpStatus.CONFLICT); } return new BusinessException(code, fallback + ": " + (desc == null ? e.getStatus().getCode() : desc), HttpStatus.CONFLICT); }
    private interface GrpcCall<T> { T execute(); }
}
