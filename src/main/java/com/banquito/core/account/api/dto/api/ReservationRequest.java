package com.banquito.core.account.api.dto.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ReservationRequest(
        @NotBlank @Size(max = 80) String batchId,
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "correlationId debe contener un UUID válido")
        String correlationId,
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "companyCustomerUuid debe contener un UUID válido")
        String companyCustomerUuid,
        @NotBlank @Size(max = 24) String mainAccountNumber,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal totalAmount,
        @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal commissionAmount,
        @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal commissionSubtotal,
        @Pattern(regexp = "^(SWITCH_WEB|SWITCH_SFTP|SWITCH_API)$", message = "channel no es válido")
        String channel,
        @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "accountingDate debe usar formato yyyy-MM-dd")
        String accountingDate
) {}
