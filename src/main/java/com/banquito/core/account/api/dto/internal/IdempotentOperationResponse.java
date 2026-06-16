package com.banquito.core.account.api.dto.internal;

public record IdempotentOperationResponse<T>(
        T body,
        boolean replayed,
        int httpStatus
) {}
