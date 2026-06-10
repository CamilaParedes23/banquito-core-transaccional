package com.banquito.core.account.api.dto.api;

public record AccountMovementRequest(@jakarta.validation.constraints.NotBlank String accountNumber, @jakarta.validation.constraints.NotNull java.math.BigDecimal amount, String subtypeCode, String channel, String externalReference, String accountingDate, String correlationId) {}
