package com.banquito.core.account.api.dto.api;

public record AccountTransactionResponse(String transactionUuid, String accountNumber, String subtypeCode, String movementType, java.math.BigDecimal amount, java.math.BigDecimal resultingAccountingBalance, java.math.BigDecimal resultingAvailableBalance, String status, String channel, String externalReference, java.time.LocalDate accountingDate, java.time.LocalDateTime timestamp) {}
