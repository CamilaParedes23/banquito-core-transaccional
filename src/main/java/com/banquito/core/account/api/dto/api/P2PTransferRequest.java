package com.banquito.core.account.api.dto.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record P2PTransferRequest(
        @NotBlank @Size(max = 24) String sourceAccountNumber,
        @NotBlank @Size(max = 24) String targetAccountNumber,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @Size(max = 300) String description,
        String accountingDate,
        @Size(max = 36) String correlationId
) {}
