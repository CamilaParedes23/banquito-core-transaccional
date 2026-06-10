package com.banquito.core.account.domain.enums;

import lombok.Getter;

@Getter
public enum ResultadoAuditoriaAccountEnum {
    OK("OK"),
    ERROR("ERROR"),
    DENEGADO("DENEGADO");

    private final String value;

    ResultadoAuditoriaAccountEnum(String value) {
        this.value = value;
    }
}
