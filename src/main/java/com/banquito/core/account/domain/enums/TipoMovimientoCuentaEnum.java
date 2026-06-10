package com.banquito.core.account.domain.enums;

import lombok.Getter;

@Getter
public enum TipoMovimientoCuentaEnum {
    DEBITO("DEBITO"),
    CREDITO("CREDITO");

    private final String value;

    TipoMovimientoCuentaEnum(String value) {
        this.value = value;
    }
}
