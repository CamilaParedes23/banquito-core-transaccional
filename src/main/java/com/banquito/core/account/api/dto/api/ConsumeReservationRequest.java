package com.banquito.core.account.api.dto.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ConsumeReservationRequest(
        @NotBlank @Size(max = 80) String paymentLineUuid,
        @NotBlank @Size(max = 20) String destinationType,
        @NotBlank @Size(max = 20) String routingCode,
        @Size(max = 24) String destinationAccountNumber,
        @Size(max = 50) String externalDestinationAccount,
        @NotBlank @Size(max = 30) String beneficiaryIdentification,
        @NotBlank @Size(max = 180) String beneficiaryName,
        @Email @Size(max = 160) String beneficiaryEmail,
        @Size(max = 300) String concept,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        String accountingDate,
        @Size(max = 36) String correlationId
) {}
