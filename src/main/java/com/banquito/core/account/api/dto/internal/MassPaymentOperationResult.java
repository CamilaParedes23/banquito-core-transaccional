package com.banquito.core.account.api.dto.internal;

public record MassPaymentOperationResult<T>(T body, boolean replayed) {}
