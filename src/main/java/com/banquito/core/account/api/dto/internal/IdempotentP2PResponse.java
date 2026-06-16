package com.banquito.core.account.api.dto.internal;

import com.banquito.core.account.api.dto.api.AccountTransactionResponse;

import java.util.List;

public record IdempotentP2PResponse(
        List<AccountTransactionResponse> transactions,
        boolean replayed
) {
}
