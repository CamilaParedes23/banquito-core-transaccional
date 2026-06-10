package com.banquito.core.account.api.dto.api;

public record P2PTransferRequest(@jakarta.validation.constraints.NotBlank String sourceAccountNumber, @jakarta.validation.constraints.NotBlank String targetAccountNumber, @jakarta.validation.constraints.NotNull java.math.BigDecimal amount, String description, String accountingDate, String correlationId) {}
