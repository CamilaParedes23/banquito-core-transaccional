package com.banquito.core.account.api.dto.api;

public record ReservationResponse(String reservationUuid, String batchId, String status, java.math.BigDecimal reservedAmount, java.math.BigDecimal consumedOnUs, java.math.BigDecimal consumedOffUs, java.math.BigDecimal releasedAmount, String mainAccountNumber) {}
