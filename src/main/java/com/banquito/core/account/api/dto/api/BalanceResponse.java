package com.banquito.core.account.api.dto.api;

public record BalanceResponse(String accountNumber, String status, java.math.BigDecimal accountingBalance, java.math.BigDecimal availableBalance, java.math.BigDecimal withheldAmount) {}
