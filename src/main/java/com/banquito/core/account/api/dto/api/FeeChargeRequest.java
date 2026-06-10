package com.banquito.core.account.api.dto.api;

public record FeeChargeRequest(java.math.BigDecimal amount, String accountingDate, String correlationId, String externalReference) {}
