package com.banquito.core.account.application.service;

import com.banquito.core.account.domain.model.OutboxEvent;
import com.banquito.core.account.infrastructure.integration.DocumentInternalClient;
import com.banquito.core.account.infrastructure.integration.NotificationInternalClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OutboxIntegrationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxIntegrationDispatcher.class);
    private static final List<String> SUPPORTED_EVENTS =
            List.of("P2P_TRANSFER_COMPLETED", "ONUS_PAYMENT_COMPLETED",
                    "TELLER_DEPOSIT_COMPLETED", "TELLER_WITHDRAWAL_COMPLETED");

    private final OutboxEventService outboxEventService;
    private final NotificationInternalClient notificationClient;
    private final DocumentInternalClient documentClient;
    private final TransactionDocumentLinkService documentLinkService;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final int maxAttempts;
    private final boolean enabled;

    public OutboxIntegrationDispatcher(
            OutboxEventService outboxEventService,
            NotificationInternalClient notificationClient,
            DocumentInternalClient documentClient,
            TransactionDocumentLinkService documentLinkService,
            ObjectMapper objectMapper,
            @Value("${banquito.integration.outbox.enabled:true}") boolean enabled,
            @Value("${banquito.integration.outbox.batch-size:20}") int batchSize,
            @Value("${banquito.integration.outbox.max-attempts:5}") int maxAttempts) {
        this.outboxEventService = outboxEventService;
        this.notificationClient = notificationClient;
        this.documentClient = documentClient;
        this.documentLinkService = documentLinkService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${banquito.integration.outbox.fixed-delay-ms:5000}")
    public void dispatch() {
        if (!enabled) return;
        List<OutboxEvent> events =
                outboxEventService.findDispatchable(SUPPORTED_EVENTS, maxAttempts, batchSize);
        for (OutboxEvent event : events) {
            try {
                Map<String, Object> payload = objectMapper.readValue(
                        event.getPayloadJson(),
                        new TypeReference<Map<String, Object>>() {}
                );
                if ("P2P_TRANSFER_COMPLETED".equals(event.getTipoEvento())) {
                    dispatchP2P(event, payload);
                } else if ("ONUS_PAYMENT_COMPLETED".equals(event.getTipoEvento())) {
                    dispatchOnUs(event, payload);
                } else if ("TELLER_DEPOSIT_COMPLETED".equals(event.getTipoEvento())
                        || "TELLER_WITHDRAWAL_COMPLETED".equals(event.getTipoEvento())) {
                    dispatchTeller(event, payload);
                }
                outboxEventService.markPublished(event.getId());
            } catch (Exception exception) {
                log.warn("No fue posible despachar outbox {} tipo {}: {}",
                        event.getUuidEvento(), event.getTipoEvento(), exception.getMessage());
                outboxEventService.markError(event.getId(), exception.getMessage(), maxAttempts);
            }
        }
    }

    private void dispatchP2P(OutboxEvent event, Map<String, Object> payload) throws Exception {
        String canonicalReceipt = objectMapper.writeValueAsString(payload);
        String documentUuid = documentClient.registerReceipt(
                event.getUuidEvento(),
                string(payload, "correlationId"),
                "ACCOUNT_P2P",
                "comprobante-p2p-" + string(payload, "correlationId") + ".json",
                canonicalReceipt,
                Map.of(
                        "operationType", "TRANSFERENCIA_P2P",
                        "debitTransactionUuid", string(payload, "debitTransactionUuid"),
                        "creditTransactionUuid", string(payload, "creditTransactionUuid")
                )
        );

        documentLinkService.link(
                documentUuid,
                string(payload, "debitTransactionUuid"),
                string(payload, "creditTransactionUuid")
        );

        notificationClient.request(notification(
                event,
                payload,
                "TRANSFER_SENT",
                string(payload, "sourceCustomerUuid"),
                "CLIENTE",
                nullableString(payload, "sourceEmail"),
                string(payload, "sourceHolderName"),
                "TRANSFER_SENT_EMAIL",
                documentUuid,
                Map.of(
                        "nombre", string(payload, "sourceHolderName"),
                        "monto", amount(payload),
                        "cuentaOrigen", string(payload, "sourceAccountNumber"),
                        "cuentaDestino", string(payload, "targetAccountNumber"),
                        "comprobante", string(payload, "receiptNumber")
                )
        ));

        notificationClient.request(notification(
                event,
                payload,
                "TRANSFER_RECEIVED",
                string(payload, "targetCustomerUuid"),
                "CLIENTE",
                nullableString(payload, "targetEmail"),
                string(payload, "targetHolderName"),
                "TRANSFER_RECEIVED_EMAIL",
                documentUuid,
                Map.of(
                        "nombre", string(payload, "targetHolderName"),
                        "monto", amount(payload),
                        "cuentaDestino", string(payload, "targetAccountNumber"),
                        "ordenante", string(payload, "sourceHolderName"),
                        "comprobante", string(payload, "receiptNumber")
                )
        ));
    }

    private void dispatchOnUs(OutboxEvent event, Map<String, Object> payload) throws Exception {
        String canonicalReceipt = objectMapper.writeValueAsString(payload);
        String documentUuid = documentClient.registerReceipt(
                event.getUuidEvento(),
                string(payload, "correlationId"),
                "MASS_PAYMENT_ONUS",
                "comprobante-onus-" + string(payload, "paymentLineUuid") + ".json",
                canonicalReceipt,
                Map.of(
                        "operationType", "PAGO_MASIVO_ONUS",
                        "transactionUuid", string(payload, "transactionUuid"),
                        "paymentLineUuid", string(payload, "paymentLineUuid")
                )
        );

        documentLinkService.link(documentUuid, string(payload, "transactionUuid"));

        notificationClient.request(notification(
                event,
                payload,
                "PAYMENT_LINE_SUCCESS",
                string(payload, "beneficiaryCustomerUuid"),
                "CLIENTE",
                nullableString(payload, "beneficiaryEmail"),
                string(payload, "beneficiaryName"),
                "PAYMENT_LINE_SUCCESS_EMAIL",
                documentUuid,
                Map.of(
                        "nombre", string(payload, "beneficiaryName"),
                        "monto", amount(payload),
                        "concepto", string(payload, "concept"),
                        "empresa", string(payload, "companyName"),
                        "comprobante", string(payload, "receiptNumber")
                )
        ));
    }

    private void dispatchTeller(OutboxEvent event, Map<String, Object> payload) throws Exception {
        String transactionUuid = string(payload, "transactionUuid");
        String operationName = string(payload, "operationName");
        String canonicalReceipt = objectMapper.writeValueAsString(payload);
        String documentUuid = documentClient.registerReceipt(
                event.getUuidEvento(),
                string(payload, "correlationId"),
                "ACCOUNT_TELLER_" + operationName,
                "comprobante-ventanilla-" + transactionUuid + ".json",
                canonicalReceipt,
                Map.of(
                        "operationType", operationName,
                        "transactionUuid", transactionUuid,
                        "receiptNumber", string(payload, "receiptNumber")
                )
        );
        documentLinkService.link(documentUuid, transactionUuid);

        Map<String, Object> request = notification(
                event,
                payload,
                "TELLER_" + operationName + "_COMPLETED",
                string(payload, "customerUuid"),
                "CLIENTE",
                nullableString(payload, "customerEmail"),
                string(payload, "holderName"),
                null,
                documentUuid,
                Map.of(
                        "nombre", string(payload, "holderName"),
                        "monto", amount(payload),
                        "cuenta", string(payload, "accountNumber"),
                        "operacion", operationName,
                        "comprobante", string(payload, "receiptNumber")
                )
        );
        request.put("subject", "Comprobante de " + operationName.toLowerCase() + " en ventanilla");
        request.put("body", "Se registró un " + operationName.toLowerCase()
                + " por " + amount(payload) + " en la cuenta " + string(payload, "accountNumber")
                + ". Comprobante: " + string(payload, "receiptNumber"));
        notificationClient.request(request);
    }

    private Map<String, Object> notification(OutboxEvent event,
                                             Map<String, Object> payload,
                                             String eventType,
                                             String actorUuid,
                                             String actorType,
                                             String recipient,
                                             String recipientName,
                                             String templateCode,
                                             String evidenceDocumentUuid,
                                             Map<String, Object> templatePayload) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sourceEventUuid", event.getUuidEvento());
        request.put("correlationId", string(payload, "correlationId"));
        request.put("eventType", eventType);
        request.put("originService", "core-account-service");
        request.put("priority", "NORMAL");
        request.put("channelType", "EMAIL");
        request.put("actorUuid", actorUuid);
        request.put("actorType", actorType);
        request.put("recipient", recipient);
        request.put("recipientName", recipientName);
        request.put("templateCode", templateCode);
        request.put("payload", templatePayload);
        request.put("evidenceDocumentUuid", evidenceDocumentUuid);
        return request;
    }

    private String string(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : value.toString();
    }

    private String nullableString(Map<String, Object> payload, String key) {
        String value = string(payload, key);
        return value.isBlank() ? null : value;
    }

    private String amount(Map<String, Object> payload) {
        Object value = payload.get("amount");
        return value == null ? BigDecimal.ZERO.setScale(2).toPlainString() : value.toString();
    }
}
