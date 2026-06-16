package com.banquito.core.account.api.dto.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AccountMovementRequest(
        @NotBlank @Size(max = 24) String accountNumber,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @Size(max = 40) String subtypeCode,
        @Size(max = 30) String channel,
        @Size(max = 200) String externalReference,
        String accountingDate,
        @Size(max = 36) String correlationId
) {}
