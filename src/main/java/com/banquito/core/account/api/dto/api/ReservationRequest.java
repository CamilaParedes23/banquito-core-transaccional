package com.banquito.core.account.api.dto.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ReservationRequest(
        @NotBlank @Size(max = 80) String batchId,
        @NotBlank @Size(max = 36) String correlationId,
        @NotBlank @Size(max = 36) String companyCustomerUuid,
        @NotBlank @Size(max = 24) String mainAccountNumber,
        @NotNull @DecimalMin(value = "0.01") BigDecimal totalAmount,
        @DecimalMin(value = "0.00") BigDecimal commissionAmount,
        @Size(max = 30) String channel,
        String accountingDate
) {}
