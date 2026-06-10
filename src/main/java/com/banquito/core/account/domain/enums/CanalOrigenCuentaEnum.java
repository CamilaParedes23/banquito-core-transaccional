package com.banquito.core.account.domain.enums;

import lombok.Getter;

@Getter
public enum CanalOrigenCuentaEnum {
    VENTANILLA("VENTANILLA"),
    BANCA_WEB("BANCA_WEB"),
    SWITCH("SWITCH"),
    ATM("ATM"),
    CORE_INTERNO("CORE_INTERNO");

    private final String value;

    CanalOrigenCuentaEnum(String value) {
        this.value = value;
    }
}
