package com.banquito.core.account.api.dto.api;

public record BlockResponse(String blockUuid, String accountNumber, java.math.BigDecimal blockedAmount, String status, String reason) {}
