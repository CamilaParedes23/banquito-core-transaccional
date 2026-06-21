package com.banquito.core.account.api.dto.api;

import java.math.BigDecimal;

public record CompanyAccountValidationResponse(
        Boolean valid,
        String code,
        String message,
        String companyCustomerUuid,
        String mainAccountNumber,
        String accountUuid,
        String accountStatus,
        String accountPurpose,
        Boolean massPaymentMainAccount,
        BigDecimal accountingBalance,
        BigDecimal availableBalance,
        BigDecimal requestedAmount,
        Boolean amountCovered,
        Boolean overdraftAllowed,
        BigDecimal overdraftLimit,
        BigDecimal overdraftAvailable
) {}
