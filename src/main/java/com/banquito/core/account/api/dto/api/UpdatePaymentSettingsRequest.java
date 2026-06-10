package com.banquito.core.account.api.dto.api;

public record UpdatePaymentSettingsRequest(Boolean favoritePaymentAccount, Boolean massPaymentMainAccount, String accountPurpose, String operationalAlias) {}
