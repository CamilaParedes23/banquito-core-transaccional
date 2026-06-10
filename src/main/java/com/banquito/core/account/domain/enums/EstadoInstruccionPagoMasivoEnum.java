package com.banquito.core.account.domain.enums;

import lombok.Getter;

@Getter
public enum EstadoInstruccionPagoMasivoEnum {
    RECIBIDA("RECIBIDA"),
    VALIDADA("VALIDADA"),
    EJECUTADA("EJECUTADA"),
    RECHAZADA("RECHAZADA"),
    REVERSADA("REVERSADA");

    private final String value;

    EstadoInstruccionPagoMasivoEnum(String value) {
        this.value = value;
    }
}
