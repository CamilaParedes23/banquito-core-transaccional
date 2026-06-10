package com.banquito.core.account.api.dto.api;

public record ReservationRequest(@jakarta.validation.constraints.NotBlank String batchId, @jakarta.validation.constraints.NotBlank String correlationId, @jakarta.validation.constraints.NotBlank String companyCustomerUuid, @jakarta.validation.constraints.NotBlank String mainAccountNumber, @jakarta.validation.constraints.NotNull java.math.BigDecimal totalAmount, java.math.BigDecimal commissionAmount, String channel, String accountingDate) {}
