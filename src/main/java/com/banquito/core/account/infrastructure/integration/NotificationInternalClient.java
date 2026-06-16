package com.banquito.core.account.infrastructure.integration;

import com.banquito.core.account.shared.exception.BusinessException;
import com.banquito.platform.notification.grpc.NotificationInternalServiceGrpc;
import com.banquito.platform.notification.grpc.SendTemplatedNotificationRequest;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class NotificationInternalClient {

    private static final Metadata.Key<String> INTERNAL_SERVICE_KEY_HEADER =
            Metadata.Key.of("x-internal-service-key", Metadata.ASCII_STRING_MARSHALLER);

    private final String host;
    private final int port;
    private final String internalServiceKey;
    private final long timeoutMs;
    private final ObjectMapper objectMapper;

    private ManagedChannel channel;
    private NotificationInternalServiceGrpc.NotificationInternalServiceBlockingStub stub;

    public NotificationInternalClient(
            @Value("${banquito.integration.notification-grpc-host:notification-service}") String host,
            @Value("${banquito.integration.notification-grpc-port:9097}") int port,
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
        stub = NotificationInternalServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> request(Map<String, Object> payload) {
        try {
            Object templatePayload = payload.get("payload");
            String payloadJson = objectMapper.writeValueAsString(
                    templatePayload instanceof Map<?, ?> ? templatePayload : Map.of()
            );

            SendTemplatedNotificationRequest.Builder requestBuilder =
                    SendTemplatedNotificationRequest.newBuilder()
                            .setSourceEventUuid(string(payload, "sourceEventUuid"))
                            .setCorrelationId(string(payload, "correlationId"))
                            .setEventType(string(payload, "eventType"))
                            .setOriginService(string(payload, "originService"))
                            .setPriority(string(payload, "priority"))
                            .setChannelType(string(payload, "channelType"))
                            .setActorUuid(string(payload, "actorUuid"))
                            .setActorType(string(payload, "actorType"))
                            .setRecipientName(string(payload, "recipientName"))
                            .setTemplateCode(string(payload, "templateCode"))
                            .setSubject(string(payload, "subject"))
                            .setBody(string(payload, "body"))
                            .setPayloadJson(payloadJson)
                            .setEvidenceDocumentUuid(string(payload, "evidenceDocumentUuid"));

            String recipient = string(payload, "recipient");
            if (!recipient.isBlank()) {
                requestBuilder.setRecipient(recipient);
            }

            var response = stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                    .sendTemplatedNotification(requestBuilder.build());

            if (!"ENVIADA".equals(response.getStatus())) {
                throw new BusinessException(
                        "NOTIFICATION_GRPC_NOT_SENT",
                        "Notification Service no confirmó el envío SMTP: " + response.getMessage(),
                        HttpStatus.SERVICE_UNAVAILABLE
                );
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("notificationUuid", response.getNotificationUuid());
            result.put("status", response.getStatus());
            result.put("message", response.getMessage());
            return result;
        } catch (StatusRuntimeException exception) {
            throw translate(
                    "NOTIFICATION_GRPC_REQUEST_ERROR",
                    "No fue posible solicitar la notificación por gRPC",
                    exception
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                    "NOTIFICATION_GRPC_PAYLOAD_ERROR",
                    "No fue posible construir la notificación: " + exception.getMessage(),
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

    private String string(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : value.toString();
    }
}
