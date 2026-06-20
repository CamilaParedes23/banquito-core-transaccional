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
@Table(name = "RESERVA_PAGO_MASIVO")
public class ReservaPagoMasivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "UUID_RESERVA", length = 36, nullable = false)
    private String uuidReserva;
    @Column(name = "BATCH_ID_EXTERNO", length = 80, nullable = false)
    private String batchIdExterno;
    @Column(name = "UUID_CORRELACION", length = 36, nullable = false)
    private String uuidCorrelacion;
    @Column(name = "UUID_CLIENTE_EMPRESA", length = 36, nullable = false)
    private String uuidClienteEmpresa;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CUENTA_MATRIZ_ID", nullable = false)
    private Cuenta cuentaMatriz;
    @Column(name = "NUMERO_CUENTA_MATRIZ", length = 24, nullable = false)
    private String numeroCuentaMatriz;
    @Enumerated(EnumType.STRING)
    @Column(name = "CANAL_ORIGEN", length = 20, nullable = false)
    private CanalOrigenReservaEnum canalOrigen;
    @Column(name = "MONTO_TOTAL_LOTE", precision = 19, scale = 2, nullable = false)
    private BigDecimal montoTotalLote;
    @Column(name = "MONTO_COMISION", precision = 19, scale = 2, nullable = false)
    private BigDecimal montoComision;
    @Column(name = "MONTO_COMISION_COBRADO", precision = 19, scale = 2, nullable = false)
    private BigDecimal montoComisionCobrado;
    @Column(name = "COMISION_LIQUIDADA", nullable = false)
    private Boolean comisionLiquidada;
    @Column(name = "MONTO_RESERVADO", precision = 19, scale = 2, nullable = false)
    private BigDecimal montoReservado;
    @Column(name = "MONTO_CONSUMIDO_ONUS", precision = 19, scale = 2, nullable = false)
    private BigDecimal montoConsumidoOnus;
    @Column(name = "MONTO_CONSUMIDO_OFFUS", precision = 19, scale = 2, nullable = false)
    private BigDecimal montoConsumidoOffus;
    @Column(name = "MONTO_LIBERADO", precision = 19, scale = 2, nullable = false)
    private BigDecimal montoLiberado;
    @Column(name = "ASIENTO_RESERVA_UUID", length = 36)
    private String asientoReservaUuid;
    @Column(name = "UUID_TRANSACCION_FONDEO", length = 36)
    private String uuidTransaccionFondeo;
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", length = 25, nullable = false)
    private EstadoReservaPagoMasivoEnum estado;
    @Column(name = "FECHA_CONTABLE", nullable = false)
    private LocalDate fechaContable;
    @Column(name = "FECHA_CREACION", nullable = false)
    private LocalDateTime fechaCreacion;
    @Column(name = "FECHA_ACTUALIZACION", nullable = false)
    private LocalDateTime fechaActualizacion;
    @Column(name = "FECHA_CIERRE")
    private LocalDateTime fechaCierre;
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;


    public ReservaPagoMasivo() {}
    public ReservaPagoMasivo(Long id) { this.id = id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReservaPagoMasivo that)) return false;
        if (this.id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    @Override
    public String toString() {
        return "ReservaPagoMasivo{" +
                "id=" + id +
                ", uuidReserva=" + uuidReserva +
                ", batchIdExterno=" + batchIdExterno +
                ", estado=" + estado +
                "}";
    }
}
