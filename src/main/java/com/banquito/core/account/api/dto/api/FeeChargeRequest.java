package com.banquito.core.account.api.dto.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FeeChargeRequest(
        @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "accountingDate debe usar formato yyyy-MM-dd")
        String accountingDate,
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "correlationId debe contener un UUID válido")
        String correlationId,
        @Size(max = 120) String externalReference
) {}
