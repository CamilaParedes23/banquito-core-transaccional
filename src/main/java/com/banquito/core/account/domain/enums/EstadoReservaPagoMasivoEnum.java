package com.banquito.core.account.domain.enums;

import lombok.Getter;

@Getter
public enum EstadoReservaPagoMasivoEnum {
    CREADA("CREADA"),
    ACTIVA("ACTIVA"),
    CONSUMIDA_PARCIAL("CONSUMIDA_PARCIAL"),
    CONSUMIDA_TOTAL("CONSUMIDA_TOTAL"),
    LIBERADA("LIBERADA"),
    REVERSADA("REVERSADA"),
    FALLIDA("FALLIDA");

    private final String value;

    EstadoReservaPagoMasivoEnum(String value) {
        this.value = value;
    }
}
