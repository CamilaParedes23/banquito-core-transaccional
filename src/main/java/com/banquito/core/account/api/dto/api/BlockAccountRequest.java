package com.banquito.core.account.api.dto.api;

public record BlockAccountRequest(@jakarta.validation.constraints.NotNull java.math.BigDecimal amount, @jakarta.validation.constraints.NotBlank String reason, String orderingAuthority, String userCoreUuid) {}
