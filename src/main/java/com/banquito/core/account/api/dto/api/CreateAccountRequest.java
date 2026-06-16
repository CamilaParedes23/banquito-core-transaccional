package com.banquito.core.account.api.dto.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank @Size(max = 36) String customerUuid,
        @NotBlank @Size(max = 30) String identification,
        @NotBlank @Size(max = 180) String holderName,
        @NotBlank @Size(max = 20) String branchCode,
        @NotBlank @Size(max = 40) String subtypeCode,
        @DecimalMin(value = "0.00") BigDecimal initialBalance,
        Boolean favoritePaymentAccount,
        Boolean massPaymentMainAccount,
        @Size(max = 30) String accountPurpose,
        @Size(max = 120) String operationalAlias
) {}
