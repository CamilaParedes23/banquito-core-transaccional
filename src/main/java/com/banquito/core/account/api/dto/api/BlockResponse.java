package com.banquito.core.account.api.dto.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BlockResponse(
        String blockUuid,
        String accountNumber,
        BigDecimal blockedAmount,
        String status,
        String reason,
        String orderingAuthority,
        String actorUuid,
        LocalDateTime blockedAt,
        LocalDateTime expiresAt,
        LocalDateTime releasedAt
) {}
