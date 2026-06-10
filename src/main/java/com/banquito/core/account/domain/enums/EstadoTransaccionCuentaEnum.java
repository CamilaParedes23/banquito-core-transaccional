package com.banquito.core.account.domain.enums;

import lombok.Getter;

@Getter
public enum EstadoTransaccionCuentaEnum {
    APLICADA("APLICADA"),
    RECHAZADA("RECHAZADA"),
    REVERSADA("REVERSADA"),
    COMPENSADA("COMPENSADA"),
    PENDIENTE_CONTABLE("PENDIENTE_CONTABLE");

    private final String value;

    EstadoTransaccionCuentaEnum(String value) {
        this.value = value;
    }
}
