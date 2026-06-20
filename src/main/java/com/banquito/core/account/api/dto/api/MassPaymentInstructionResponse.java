package com.banquito.core.account.api.dto.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MassPaymentInstructionResponse(
        String instructionUuid,
        String reservationUuid,
        String batchId,
        String paymentLineUuid,
        String correlationId,
        String destinationType,
        String routingCode,
        String destinationAccountNumber,
        String externalDestinationAccount,
        String beneficiaryIdentification,
        String beneficiaryName,
        String beneficiaryEmail,
        String concept,
        BigDecimal amount,
        String status,
        String rejectionReason,
        String transactionUuid,
        String journalEntryUuid,
        String receiptNumber,
        LocalDate accountingDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
