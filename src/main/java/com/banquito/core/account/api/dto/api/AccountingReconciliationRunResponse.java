package com.banquito.core.account.api.dto.api;

public record AccountingReconciliationRunResponse(
        int inspected,
        int reconciled,
        int stillPending,
        int requiresManualReview
) {}
