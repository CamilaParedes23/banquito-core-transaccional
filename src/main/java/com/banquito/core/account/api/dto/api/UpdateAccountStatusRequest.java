package com.banquito.core.account.api.dto.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAccountStatusRequest(
        @NotBlank @Size(max = 15) String status,
        @NotBlank @Size(max = 300) String reason,
        @Size(max = 36) String userCoreUuid
) {}
