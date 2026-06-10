package com.banquito.core.account.domain.enums;

import lombok.Getter;

@Getter
public enum TipoDestinoPagoMasivoEnum {
    ON_US("ON_US"),
    OFF_US("OFF_US");

    private final String value;

    TipoDestinoPagoMasivoEnum(String value) {
        this.value = value;
    }
}
