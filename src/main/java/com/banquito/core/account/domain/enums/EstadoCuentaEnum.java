package com.banquito.core.account.domain.enums;

import lombok.Getter;

@Getter
public enum EstadoCuentaEnum {
    ACTIVA("ACTIVA"),
    INACTIVA("INACTIVA"),
    BLOQUEADA("BLOQUEADA"),
    SUSPENDIDA("SUSPENDIDA"),
    CERRADA("CERRADA");

    private final String value;

    EstadoCuentaEnum(String value) {
        this.value = value;
    }
}
