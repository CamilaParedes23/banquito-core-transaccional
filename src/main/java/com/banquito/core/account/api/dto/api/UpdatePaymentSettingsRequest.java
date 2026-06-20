package com.banquito.core.account.api.dto.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record UpdatePaymentSettingsRequest(
        Boolean favoritePaymentAccount,
        Boolean massPaymentMainAccount,
        String accountPurpose,
        String operationalAlias,
        Boolean overdraftAllowed,
        @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal overdraftLimit
) {}
