package com.banquito.core.account.api.dto.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CompanyAccountValidationRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "companyCustomerUuid debe contener un UUID válido")
        String companyCustomerUuid,
        @NotBlank @Size(max = 24) String mainAccountNumber,
        @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount
) {}
