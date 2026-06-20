package com.banquito.core.account.api.dto.api;

public record P2PBeneficiaryValidationResponse(
        boolean accountExists,
        String accountStatus,
        String holderDisplayName,
        String maskedAccountNumber,
        String institution
) {
}
