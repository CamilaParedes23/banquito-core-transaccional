package com.banquito.core.account.api.dto.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReservationResponse(
        String reservationUuid,
        String batchId,
        String correlationId,
        String status,
        BigDecimal totalBatchAmount,
        BigDecimal commissionAmount,
        BigDecimal reservedAmount,
        BigDecimal consumedOnUs,
        BigDecimal consumedOffUs,
        BigDecimal chargedCommission,
        Boolean commissionSettled,
        BigDecimal releasedAmount,
        BigDecimal remainingAmount,
        String mainAccountNumber,
        String fundingTransactionUuid,
        String fundingJournalEntryUuid,
        LocalDate accountingDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime closedAt
) {}
