package com.banquito.core.account.infrastructure.integration;

import com.banquito.core.account.shared.exception.BusinessException;
import com.banquito.platform.document.infrastructure.grpc.DocumentQueryServiceGrpc;
import com.banquito.platform.document.infrastructure.grpc.RegisterDocumentRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class DocumentInternalClient {

    private static final Metadata.Key<String> INTERNAL_SERVICE_KEY_HEADER =
            Metadata.Key.of("x-internal-service-key", Metadata.ASCII_STRING_MARSHALLER);

    private final String host;
    private final int port;
    private final String internalServiceKey;
    private final long timeoutMs;
    private final ObjectMapper objectMapper;

    private ManagedChannel channel;
    private DocumentQueryServiceGrpc.DocumentQueryServiceBlockingStub stub;

    public DocumentInternalClient(
            @Value("${banquito.integration.document-grpc-host:document-service}") String host,
            @Value("${banquito.integration.document-grpc-port:9096}") int port,
            @Value("${banquito.integration.internal-service-key}") String internalServiceKey,
            @Value("${banquito.integration.grpc-timeout-ms:10000}") long timeoutMs,
            ObjectMapper objectMapper) {
        this.host = host;
        this.port = port;
        this.internalServiceKey = internalServiceKey;
        this.timeoutMs = timeoutMs;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        Metadata headers = new Metadata();
        headers.put(INTERNAL_SERVICE_KEY_HEADER, internalServiceKey);
        stub = DocumentQueryServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    public String registerReceipt(String sourceEventUuid,
                                  String correlationId,
                                  String businessContext,
                                  String fileName,
                                  String textPayload,
                                  Map<String, Object> metadata) {
        try {
            RegisterDocumentRequest request = RegisterDocumentRequest.newBuilder()
                    .setBusinessContext(nonNull(businessContext))
                    .setDocumentType("ACCOUNT_TRANSACTION_RECEIPT")
                    .setBusinessReferenceUuid(nonNull(sourceEventUuid))
                    .setFileName(nonNull(fileName))
                    .setMimeType("application/json")
                    .setHashSha256(sha256(textPayload))
                    .setTextPayload(nonNull(textPayload))
                    .setCreatedBy("core-account-service")
                    .setCorrelationId(nonNull(correlationId))
                    .setMetadataJson(objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata))
                    .build();

            var response = stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                    .registerDocument(request);

            if (response.getDocumentUuid().isBlank()) {
                throw new BusinessException(
                        "DOCUMENT_GRPC_EMPTY_RESPONSE",
                        "Document Service no devolvió documentUuid",
                        HttpStatus.SERVICE_UNAVAILABLE
                );
            }
            return response.getDocumentUuid();
        } catch (StatusRuntimeException exception) {
            throw translate(
                    "DOCUMENT_GRPC_REGISTER_ERROR",
                    "No fue posible registrar la evidencia documental por gRPC",
                    exception
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                    "DOCUMENT_GRPC_PAYLOAD_ERROR",
                    "No fue posible construir la evidencia documental: " + exception.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private BusinessException translate(String code, String fallback, StatusRuntimeException exception) {
        String description = exception.getStatus().getDescription();
        if (description != null && description.contains("|")) {
            String[] parts = description.split("\\|", 2);
            return new BusinessException(parts[0], parts[1], HttpStatus.SERVICE_UNAVAILABLE);
        }
        return new BusinessException(
                code,
                fallback + ": " + (description == null ? exception.getStatus().getCode() : description),
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(nonNull(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible calcular el hash del comprobante", exception);
        }
    }

    private String nonNull(String value) {
        return value == null ? "" : value;
    }
}
