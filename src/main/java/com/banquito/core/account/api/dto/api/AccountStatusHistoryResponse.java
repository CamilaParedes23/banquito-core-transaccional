package com.banquito.core.account.api.dto.api;

import java.time.LocalDateTime;

public record AccountStatusHistoryResponse(
        String accountNumber,
        String previousStatus,
        String newStatus,
        String reason,
        String actorUuid,
        LocalDateTime changedAt
) {}
