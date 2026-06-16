package com.banquito.core.account.api.dto.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReverseTransactionRequest(
        @NotBlank @Size(max = 300) String reason,
        @NotBlank @Size(max = 36) String userCoreUuid,
        @Size(max = 36) String correlationId
) {}
