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
@Table(name = "MOVIMIENTO_RESERVA_PAGO")
public class MovimientoReservaPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "UUID_MOVIMIENTO", length = 36, nullable = false)
    private String uuidMovimiento;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RESERVA_PAGO_MASIVO_ID", nullable = false)
    private ReservaPagoMasivo reservaPagoMasivo;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "INSTRUCCION_PAGO_CORE_ID")
    private InstruccionPagoMasivoCore instruccionPagoCore;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TRANSACCION_CUENTA_ID")
    private TransaccionCuenta transaccionCuenta;
    @Column(name = "ASIENTO_CONTABLE_UUID", length = 36)
    private String asientoContableUuid;
    @Column(name = "PAYMENT_LINE_UUID", length = 36)
    private String paymentLineUuid;
    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_MOVIMIENTO", length = 30, nullable = false)
    private TipoMovimientoReservaEnum tipoMovimiento;
    @Column(name = "MONTO", precision = 19, scale = 2, nullable = false)
    private BigDecimal monto;
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", length = 20, nullable = false)
    private EstadoMovimientoReservaEnum estado;
    @Column(name = "FECHA_CONTABLE", nullable = false)
    private LocalDate fechaContable;
    @Column(name = "FECHA_CREACION", nullable = false)
    private LocalDateTime fechaCreacion;


    public MovimientoReservaPago() {}
    public MovimientoReservaPago(Long id) { this.id = id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MovimientoReservaPago that)) return false;
        if (this.id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    @Override
    public String toString() {
        return "MovimientoReservaPago{" +
                "id=" + id +
                ", uuidMovimiento=" + uuidMovimiento +
                ", tipoMovimiento=" + tipoMovimiento +
                ", estado=" + estado +
                "}";
    }
}
