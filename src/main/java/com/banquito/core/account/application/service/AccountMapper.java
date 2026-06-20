package com.banquito.core.account.application.service;

import com.banquito.core.account.api.dto.api.*;
import com.banquito.core.account.domain.enums.EstadoReservaPagoMasivoEnum;
import com.banquito.core.account.domain.model.*;

public final class AccountMapper {
    private AccountMapper() {}
    public static AccountResponse toResponse(Cuenta c) {
        java.math.BigDecimal overdraftLimit = Boolean.TRUE.equals(c.getPermiteSobregiro())
                ? c.getLimiteSobregiro()
                : java.math.BigDecimal.ZERO.setScale(2);
        java.math.BigDecimal overdraftUsed = c.getSaldoDisponible()
                .min(java.math.BigDecimal.ZERO.setScale(2))
                .abs();
        java.math.BigDecimal overdraftAvailable = overdraftLimit
                .subtract(overdraftUsed)
                .max(java.math.BigDecimal.ZERO.setScale(2));
        return new AccountResponse(
                c.getUuidCuenta(),
                c.getNumeroCuenta(),
                c.getUuidCliente(),
                c.getIdentificacionTitular(),
                c.getNombreTitularReferencia(),
                c.getCodigoSucursal(),
                c.getCodigoSubtipoCuenta(),
                c.getEstado().name(),
                c.getSaldoContable(),
                c.getSaldoDisponible(),
                c.getMontoRetenido(),
                c.getEsCuentaFavoritaPagos(),
                c.getEsCuentaMatrizPagos(),
                c.getPropositoCuenta().name(),
                c.getAliasOperativo(),
                c.getPermiteSobregiro(),
                overdraftLimit,
                overdraftAvailable);
    }
    public static BalanceResponse toBalance(Cuenta c) { return new BalanceResponse(c.getNumeroCuenta(), c.getEstado().name(), c.getSaldoContable(), c.getSaldoDisponible(), c.getMontoRetenido()); }
    public static AccountTransactionResponse toTransaction(TransaccionCuenta t) {
        return toTransaction(t, t.getCodigoSubtipoTransaccion());
    }

    public static AccountTransactionResponse toTransaction(TransaccionCuenta t, String subtypeName) {
        return new AccountTransactionResponse(
                t.getUuidTransaccion(),
                t.getCuenta().getNumeroCuenta(),
                t.getCodigoSubtipoTransaccion(),
                subtypeName,
                t.getTipoMovimiento().name(),
                t.getMonto(),
                t.getSaldoContableResultante(),
                t.getSaldoDisponibleResultante(),
                t.getEstado().name(),
                t.getCanalOrigen().name(),
                t.getReferenciaExterna(),
                t.getFechaContable(),
                t.getTimestampTransaccion(),
                t.getNumeroComprobante(),
                t.getUuidDocumentoComprobante(),
                t.getUuidCorrelacion(),
                t.getTransaccionReversada() == null ? null : t.getTransaccionReversada().getUuidTransaccion()
        );
    }
    public static ReservationResponse toReservation(ReservaPagoMasivo r) {
        java.math.BigDecimal chargedCommission = r.getMontoComisionCobrado() == null
                ? java.math.BigDecimal.ZERO.setScale(2)
                : r.getMontoComisionCobrado();
        java.math.BigDecimal remaining = r.getMontoReservado()
                .subtract(r.getMontoConsumidoOnus())
                .subtract(r.getMontoConsumidoOffus())
                .subtract(r.getMontoLiberado());
        if (r.getEstado() == EstadoReservaPagoMasivoEnum.LIBERADA
                || r.getEstado() == EstadoReservaPagoMasivoEnum.CONSUMIDA_TOTAL
                || r.getEstado() == EstadoReservaPagoMasivoEnum.REVERSADA
                || remaining.signum() < 0) {
            remaining = java.math.BigDecimal.ZERO.setScale(2);
        }
        return new ReservationResponse(
                r.getUuidReserva(),
                r.getBatchIdExterno(),
                r.getUuidCorrelacion(),
                r.getEstado().name(),
                r.getMontoTotalLote(),
                r.getMontoComision(),
                r.getMontoReservado(),
                r.getMontoConsumidoOnus(),
                r.getMontoConsumidoOffus(),
                chargedCommission,
                Boolean.TRUE.equals(r.getComisionLiquidada()),
                r.getMontoLiberado(),
                remaining,
                r.getNumeroCuentaMatriz(),
                r.getUuidTransaccionFondeo(),
                r.getAsientoReservaUuid(),
                r.getFechaContable(),
                r.getFechaCreacion(),
                r.getFechaActualizacion(),
                r.getFechaCierre());
    }

    public static MassPaymentInstructionResponse toMassPaymentInstruction(InstruccionPagoMasivoCore i) {
        return new MassPaymentInstructionResponse(
                i.getUuidInstruccion(),
                i.getReservaPagoMasivo().getUuidReserva(),
                i.getBatchIdExterno(),
                i.getPaymentLineUuid(),
                i.getUuidCorrelacion(),
                i.getTipoDestino().name(),
                i.getRoutingCodeDestino(),
                i.getNumeroCuentaDestino(),
                i.getCuentaDestinoExterna(),
                i.getIdentificacionBeneficiario(),
                i.getNombreBeneficiario(),
                i.getEmailBeneficiario(),
                i.getConcepto(),
                i.getMonto(),
                i.getEstado().name(),
                i.getMotivoRechazo(),
                i.getUuidTransaccionCore(),
                i.getAsientoContableUuid(),
                i.getNumeroComprobante(),
                i.getFechaContable(),
                i.getFechaCreacion(),
                i.getFechaActualizacion());
    }
    public static BlockResponse toBlock(BloqueoCuenta b) {
        return new BlockResponse(
                b.getUuidBloqueo(),
                b.getCuenta().getNumeroCuenta(),
                b.getMontoBloqueado(),
                b.getEstado().name(),
                b.getMotivo(),
                b.getAutoridadOrdenante(),
                b.getUuidUsuarioCore(),
                b.getFechaBloqueo(),
                b.getFechaExpiracion(),
                b.getFechaLiberacion());
    }

    public static AccountStatusHistoryResponse toStatusHistory(HistorialEstadoCuenta history) {
        return new AccountStatusHistoryResponse(
                history.getCuenta().getNumeroCuenta(),
                history.getEstadoAnterior().name(),
                history.getEstadoNuevo().name(),
                history.getMotivoCambio(),
                history.getUuidUsuarioCore(),
                history.getFechaCambio());
    }
}
