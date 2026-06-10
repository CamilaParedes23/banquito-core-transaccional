package com.banquito.core.account.domain.enums;

import lombok.Getter;

@Getter
public enum PropositoCuentaEnum {
    GENERAL("GENERAL"),
    OPERATIVA("OPERATIVA"),
    NOMINA("NOMINA"),
    IMPUESTOS("IMPUESTOS"),
    PAGOS_MASIVOS("PAGOS_MASIVOS");

    private final String value;

    PropositoCuentaEnum(String value) {
        this.value = value;
    }
}
