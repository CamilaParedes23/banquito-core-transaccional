package com.banquito.core.account.api.dto.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AccountTransactionResponse(
        String transactionUuid,
        String accountNumber,
        String subtypeCode,
        String movementType,
        BigDecimal amount,
        BigDecimal resultingAccountingBalance,
        BigDecimal resultingAvailableBalance,
        String status,
        String channel,
        String externalReference,
        LocalDate accountingDate,
        LocalDateTime timestamp,
        String receiptNumber,
        String documentReceiptUuid,
        String correlationId,
        String reversedTransactionUuid
) {}
