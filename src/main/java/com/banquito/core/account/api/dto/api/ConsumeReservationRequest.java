package com.banquito.core.account.api.dto.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ConsumeReservationRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "paymentLineUuid debe contener un UUID válido")
        String paymentLineUuid,
        @NotBlank @Pattern(regexp = "^(ON_US|OFF_US)$", message = "destinationType no es válido")
        String destinationType,
        @NotBlank @Size(max = 20) String routingCode,
        @Size(max = 24) String destinationAccountNumber,
        @Size(max = 34) String externalDestinationAccount,
        @NotBlank @Size(max = 20) String beneficiaryIdentification,
        @NotBlank @Size(max = 180) String beneficiaryName,
        @Email @Size(max = 160) String beneficiaryEmail,
        @Size(max = 250) String concept,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "accountingDate debe usar formato yyyy-MM-dd")
        String accountingDate,
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "correlationId debe contener un UUID válido")
        String correlationId
) {}
