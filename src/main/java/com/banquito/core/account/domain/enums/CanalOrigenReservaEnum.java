package com.banquito.core.account.domain.enums;

import lombok.Getter;

@Getter
public enum CanalOrigenReservaEnum {
    SWITCH_WEB("SWITCH_WEB"),
    SWITCH_SFTP("SWITCH_SFTP"),
    SWITCH_API("SWITCH_API");

    private final String value;

    CanalOrigenReservaEnum(String value) {
        this.value = value;
    }
}
