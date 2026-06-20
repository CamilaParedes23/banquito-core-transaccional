package com.banquito.core.account.api.dto.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BlockAccountRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Size(max = 300) String reason,
        @Size(max = 150) String orderingAuthority,
        @Size(max = 36) String userCoreUuid,
        LocalDateTime expiresAt
) {}
