package com.banquito.core.account.api.dto.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record P2PBeneficiaryValidationRequest(
        @NotBlank
        @Size(max = 24)
        String accountNumber
) {
}
