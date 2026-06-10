package com.banquito.core.account.application.service;

import com.banquito.core.account.api.dto.api.*;
import com.banquito.core.account.domain.model.*;

public final class AccountMapper {
    private AccountMapper() {}
    public static AccountResponse toResponse(Cuenta c) {
        return new AccountResponse(c.getUuidCuenta(), c.getNumeroCuenta(), c.getUuidCliente(), c.getIdentificacionTitular(), c.getNombreTitularReferencia(), c.getCodigoSucursal(), c.getCodigoSubtipoCuenta(), c.getEstado().name(), c.getSaldoContable(), c.getSaldoDisponible(), c.getMontoRetenido(), c.getEsCuentaFavoritaPagos(), c.getEsCuentaMatrizPagos(), c.getPropositoCuenta().name(), c.getAliasOperativo());
    }
    public static BalanceResponse toBalance(Cuenta c) { return new BalanceResponse(c.getNumeroCuenta(), c.getEstado().name(), c.getSaldoContable(), c.getSaldoDisponible(), c.getMontoRetenido()); }
    public static AccountTransactionResponse toTransaction(TransaccionCuenta t) { return new AccountTransactionResponse(t.getUuidTransaccion(), t.getCuenta().getNumeroCuenta(), t.getCodigoSubtipoTransaccion(), t.getTipoMovimiento().name(), t.getMonto(), t.getSaldoContableResultante(), t.getSaldoDisponibleResultante(), t.getEstado().name(), t.getCanalOrigen().name(), t.getReferenciaExterna(), t.getFechaContable(), t.getTimestampTransaccion()); }
    public static ReservationResponse toReservation(ReservaPagoMasivo r) { return new ReservationResponse(r.getUuidReserva(), r.getBatchIdExterno(), r.getEstado().name(), r.getMontoReservado(), r.getMontoConsumidoOnus(), r.getMontoConsumidoOffus(), r.getMontoLiberado(), r.getNumeroCuentaMatriz()); }
    public static BlockResponse toBlock(BloqueoCuenta b) { return new BlockResponse(b.getUuidBloqueo(), b.getCuenta().getNumeroCuenta(), b.getMontoBloqueado(), b.getEstado().name(), b.getMotivo()); }
}
