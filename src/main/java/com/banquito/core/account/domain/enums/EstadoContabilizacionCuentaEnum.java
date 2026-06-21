package com.banquito.core.account.domain.enums;

import lombok.Getter;

@Getter
public enum EstadoContabilizacionCuentaEnum {
    PENDIENTE_CONTABILIDAD("PENDIENTE_CONTABILIDAD"),
    CONTABILIZADA("CONTABILIZADA"),
    COMPENSADA("COMPENSADA"),
    REQUIERE_RECONCILIACION("REQUIERE_RECONCILIACION");

    private final String value;

    EstadoContabilizacionCuentaEnum(String value) {
        this.value = value;
    }
}
