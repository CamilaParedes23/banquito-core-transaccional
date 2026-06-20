package com.banquito.core.account.domain.enums;

import lombok.Getter;

@Getter
public enum EstadoBloqueoCuentaEnum {
    ACTIVO("ACTIVO"),
    LIBERADO("LIBERADO"),
    REVOCADO("REVOCADO"),
    EXPIRADO("EXPIRADO");

    private final String value;

    EstadoBloqueoCuentaEnum(String value) {
        this.value = value;
    }
}
