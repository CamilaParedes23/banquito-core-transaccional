package com.banquito.core.account.api.dto.api;

import java.math.BigDecimal;

public record AccountResponse(
        String accountUuid,
        String accountNumber,
        String customerUuid,
        String identification,
        String holderName,
        String branchCode,
        String subtypeCode,
        String status,
        BigDecimal accountingBalance,
        BigDecimal availableBalance,
        BigDecimal withheldAmount,
        Boolean favoritePaymentAccount,
        Boolean massPaymentMainAccount,
        String accountPurpose,
        String operationalAlias,
        Boolean overdraftAllowed,
        BigDecimal overdraftLimit,
        BigDecimal overdraftAvailable
) {}
