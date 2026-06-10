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
@Table(name = "CUENTA")
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "UUID_CUENTA", length = 36, nullable = false)
    private String uuidCuenta;
    @Column(name = "NUMERO_CUENTA", length = 24, nullable = false)
    private String numeroCuenta;
    @Column(name = "UUID_CLIENTE", length = 36, nullable = false)
    private String uuidCliente;
    @Column(name = "IDENTIFICACION_TITULAR", length = 20, nullable = false)
    private String identificacionTitular;
    @Column(name = "NOMBRE_TITULAR_REFERENCIA", length = 180, nullable = false)
    private String nombreTitularReferencia;
    @Column(name = "CODIGO_SUCURSAL", length = 10, nullable = false)
    private String codigoSucursal;
    @Column(name = "CODIGO_SUBTIPO_CUENTA", length = 30, nullable = false)
    private String codigoSubtipoCuenta;
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", length = 15, nullable = false)
    private EstadoCuentaEnum estado;
    @Column(name = "SALDO_CONTABLE", precision = 19, scale = 2, nullable = false)
    private BigDecimal saldoContable;
    @Column(name = "SALDO_DISPONIBLE", precision = 19, scale = 2, nullable = false)
    private BigDecimal saldoDisponible;
    @Column(name = "MONTO_RETENIDO", precision = 19, scale = 2, nullable = false)
    private BigDecimal montoRetenido;
    @Column(name = "PERMITE_SOBREGIRO", nullable = false)
    private Boolean permiteSobregiro;
    @Column(name = "LIMITE_SOBREGIRO", precision = 19, scale = 2, nullable = false)
    private BigDecimal limiteSobregiro;
    @Column(name = "ES_CUENTA_MATRIZ_PAGOS", nullable = false)
    private Boolean esCuentaMatrizPagos;
    @Column(name = "ES_CUENTA_FAVORITA_PAGOS", nullable = false)
    private Boolean esCuentaFavoritaPagos;
    @Column(name = "ALIAS_OPERATIVO", length = 80)
    private String aliasOperativo;
    @Enumerated(EnumType.STRING)
    @Column(name = "PROPOSITO_CUENTA", length = 20, nullable = false)
    private PropositoCuentaEnum propositoCuenta;
    @Column(name = "FECHA_APERTURA", nullable = false)
    private LocalDateTime fechaApertura;
    @Column(name = "FECHA_ACTUALIZACION", nullable = false)
    private LocalDateTime fechaActualizacion;
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;


    public Cuenta() {}
    public Cuenta(Long id) { this.id = id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cuenta that)) return false;
        if (this.id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    @Override
    public String toString() {
        return "Cuenta{" +
                "id=" + id +
                ", numeroCuenta=" + numeroCuenta +
                ", uuidCliente=" + uuidCliente +
                ", estado=" + estado +
                "}";
    }
}
