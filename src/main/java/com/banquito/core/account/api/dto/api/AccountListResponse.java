package com.banquito.core.account.api.dto.api;

import java.util.List;

public record AccountListResponse(
        long total,
        int page,
        int size,
        int totalPages,
        List<AccountResponse> accounts
) {}
