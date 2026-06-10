package com.banquito.core.account.api.dto.api;

public record CreateAccountRequest(@jakarta.validation.constraints.NotBlank String customerUuid, @jakarta.validation.constraints.NotBlank String identification, @jakarta.validation.constraints.NotBlank String holderName, @jakarta.validation.constraints.NotBlank String branchCode, @jakarta.validation.constraints.NotBlank String subtypeCode, java.math.BigDecimal initialBalance, Boolean favoritePaymentAccount, Boolean massPaymentMainAccount, String accountPurpose, String operationalAlias) {}
