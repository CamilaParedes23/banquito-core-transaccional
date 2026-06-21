package com.banquito.core.account.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import com.banquito.core.account.domain.enums.*;

@Getter
@Setter
@Entity
@Table(name = "TRANSACCION_CUENTA")
public class TransaccionCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "UUID_TRANSACCION", length = 36, nullable = false)
    private String uuidTransaccion;
    @Column(name = "UUID_CORRELACION", length = 36, nullable = false)
    private String uuidCorrelacion;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CUENTA_ID", nullable = false)
    private Cuenta cuenta;
    @Column(name = "CODIGO_SUBTIPO_TRANSACCION", length = 40, nullable = false)
    private String codigoSubtipoTransaccion;
    @Column(name = "ASIENTO_CONTABLE_UUID", length = 36)
    private String asientoContableUuid;
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_CONTABILIZACION", length = 40, nullable = false)
    private EstadoContabilizacionCuentaEnum estadoContabilizacion;
    @Column(name = "FECHA_CONTABILIZACION")
    private LocalDateTime fechaContabilizacion;
    @Column(name = "CODIGO_ERROR_CONTABLE", length = 80)
    private String codigoErrorContable;
    @Column(name = "MENSAJE_ERROR_CONTABLE", length = 500)
    private String mensajeErrorContable;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RESERVA_PAGO_MASIVO_ID")
    private ReservaPagoMasivo reservaPagoMasivo;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "INSTRUCCION_PAGO_CORE_ID")
    private InstruccionPagoMasivoCore instruccionPagoCore;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TRANSACCION_REVERSADA_ID")
    private TransaccionCuenta transaccionReversada;
    @Column(name = "FECHA_CONTABLE", nullable = false)
    private LocalDate fechaContable;
    @Column(name = "TIMESTAMP_TRANSACCION", nullable = false)
    private LocalDateTime timestampTransaccion;
    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_MOVIMIENTO", length = 10, nullable = false)
    private TipoMovimientoCuentaEnum tipoMovimiento;
    @Column(name = "MONTO", precision = 19, scale = 2, nullable = false)
    private BigDecimal monto;
    @Column(name = "SALDO_CONTABLE_ANTERIOR", precision = 19, scale = 2, nullable = false)
    private BigDecimal saldoContableAnterior;
    @Column(name = "SALDO_CONTABLE_RESULTANTE", precision = 19, scale = 2, nullable = false)
    private BigDecimal saldoContableResultante;
    @Column(name = "SALDO_DISPONIBLE_RESULTANTE", precision = 19, scale = 2, nullable = false)
    private BigDecimal saldoDisponibleResultante;
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", length = 20, nullable = false)
    private EstadoTransaccionCuentaEnum estado;
    @Enumerated(EnumType.STRING)
    @Column(name = "CANAL_ORIGEN", length = 30, nullable = false)
    private CanalOrigenCuentaEnum canalOrigen;
    @Column(name = "REFERENCIA_EXTERNA", length = 120)
    private String referenciaExterna;
    @Column(name = "NUMERO_COMPROBANTE", length = 80)
    private String numeroComprobante;
    @Column(name = "UUID_DOCUMENTO_COMPROBANTE", length = 36)
    private String uuidDocumentoComprobante;
    @Column(name = "MOTIVO_REVERSO", length = 300)
    private String motivoReverso;
    @Column(name = "UUID_USUARIO_REVERSO", length = 36)
    private String uuidUsuarioReverso;
    @Column(name = "FECHA_CREACION", nullable = false)
    private LocalDateTime fechaCreacion;
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;


    public TransaccionCuenta() {}
    public TransaccionCuenta(Long id) { this.id = id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransaccionCuenta that)) return false;
        if (this.id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    @Override
    public String toString() {
        return "TransaccionCuenta{" +
                "id=" + id +
                ", uuidTransaccion=" + uuidTransaccion +
                ", tipoMovimiento=" + tipoMovimiento +
                ", estado=" + estado +
                "}";
    }
}
