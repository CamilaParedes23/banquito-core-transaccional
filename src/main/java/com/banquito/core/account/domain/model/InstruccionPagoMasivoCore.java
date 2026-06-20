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
@Table(name = "INSTRUCCION_PAGO_MASIVO_CORE")
public class InstruccionPagoMasivoCore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "UUID_INSTRUCCION", length = 36, nullable = false)
    private String uuidInstruccion;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RESERVA_PAGO_MASIVO_ID", nullable = false)
    private ReservaPagoMasivo reservaPagoMasivo;
    @Column(name = "BATCH_ID_EXTERNO", length = 80, nullable = false)
    private String batchIdExterno;
    @Column(name = "PAYMENT_LINE_UUID", length = 36, nullable = false)
    private String paymentLineUuid;
    @Column(name = "UUID_CORRELACION", length = 36, nullable = false)
    private String uuidCorrelacion;
    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_DESTINO", length = 10, nullable = false)
    private TipoDestinoPagoMasivoEnum tipoDestino;
    @Column(name = "ROUTING_CODE_DESTINO", length = 20, nullable = false)
    private String routingCodeDestino;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CUENTA_DESTINO_ID")
    private Cuenta cuentaDestino;
    @Column(name = "NUMERO_CUENTA_DESTINO", length = 24)
    private String numeroCuentaDestino;
    @Column(name = "CUENTA_DESTINO_EXTERNA", length = 34)
    private String cuentaDestinoExterna;
    @Column(name = "IDENTIFICACION_BENEFICIARIO", length = 20, nullable = false)
    private String identificacionBeneficiario;
    @Column(name = "NOMBRE_BENEFICIARIO", length = 180, nullable = false)
    private String nombreBeneficiario;
    @Column(name = "EMAIL_BENEFICIARIO", length = 160)
    private String emailBeneficiario;
    @Column(name = "CONCEPTO", length = 250)
    private String concepto;
    @Column(name = "MONTO", precision = 19, scale = 2, nullable = false)
    private BigDecimal monto;
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", length = 25, nullable = false)
    private EstadoInstruccionPagoMasivoEnum estado;
    @Column(name = "MOTIVO_RECHAZO", length = 300)
    private String motivoRechazo;
    @Column(name = "UUID_TRANSACCION_CORE", length = 36)
    private String uuidTransaccionCore;
    @Column(name = "ASIENTO_CONTABLE_UUID", length = 36)
    private String asientoContableUuid;
    @Column(name = "NUMERO_COMPROBANTE", length = 80)
    private String numeroComprobante;
    @Column(name = "FECHA_CONTABLE", nullable = false)
    private LocalDate fechaContable;
    @Column(name = "FECHA_CREACION", nullable = false)
    private LocalDateTime fechaCreacion;
    @Column(name = "FECHA_ACTUALIZACION", nullable = false)
    private LocalDateTime fechaActualizacion;
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;


    public InstruccionPagoMasivoCore() {}
    public InstruccionPagoMasivoCore(Long id) { this.id = id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InstruccionPagoMasivoCore that)) return false;
        if (this.id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    @Override
    public String toString() {
        return "InstruccionPagoMasivoCore{" +
                "id=" + id +
                ", paymentLineUuid=" + paymentLineUuid +
                ", tipoDestino=" + tipoDestino +
                ", estado=" + estado +
                "}";
    }
}
