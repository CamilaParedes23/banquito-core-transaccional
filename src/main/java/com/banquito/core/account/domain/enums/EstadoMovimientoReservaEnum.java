package com.banquito.core.account.domain.enums;

import lombok.Getter;

@Getter
public enum EstadoMovimientoReservaEnum {
    APLICADO("APLICADO"),
    RECHAZADO("RECHAZADO"),
    REVERSADO("REVERSADO");

    private final String value;

    EstadoMovimientoReservaEnum(String value) {
        this.value = value;
    }
}
