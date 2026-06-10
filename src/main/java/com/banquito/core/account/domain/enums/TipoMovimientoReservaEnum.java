package com.banquito.core.account.domain.enums;

import lombok.Getter;

@Getter
public enum TipoMovimientoReservaEnum {
    RESERVA_INICIAL("RESERVA_INICIAL"),
    CONSUMO_ONUS("CONSUMO_ONUS"),
    CONSUMO_OFFUS("CONSUMO_OFFUS"),
    LIBERACION_SOBRANTE("LIBERACION_SOBRANTE"),
    REVERSO("REVERSO"),
    COMISION("COMISION");

    private final String value;

    TipoMovimientoReservaEnum(String value) {
        this.value = value;
    }
}
