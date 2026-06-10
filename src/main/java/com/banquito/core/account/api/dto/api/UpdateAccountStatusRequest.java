package com.banquito.core.account.api.dto.api;

public record UpdateAccountStatusRequest(@jakarta.validation.constraints.NotBlank String status, @jakarta.validation.constraints.NotBlank String reason, String userCoreUuid) {}
