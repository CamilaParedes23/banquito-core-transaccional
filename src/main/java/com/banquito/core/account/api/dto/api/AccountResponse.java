package com.banquito.core.account.api.dto.api;

public record AccountResponse(String accountUuid, String accountNumber, String customerUuid, String identification, String holderName, String branchCode, String subtypeCode, String status, java.math.BigDecimal accountingBalance, java.math.BigDecimal availableBalance, java.math.BigDecimal withheldAmount, Boolean favoritePaymentAccount, Boolean massPaymentMainAccount, String accountPurpose, String operationalAlias) {}
