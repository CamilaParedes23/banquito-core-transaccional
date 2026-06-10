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
@Table(name = "HISTORIAL_ESTADO_CUENTA")
public class HistorialEstadoCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CUENTA_ID", nullable = false)
    private Cuenta cuenta;
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_ANTERIOR", length = 15, nullable = false)
    private EstadoCuentaEnum estadoAnterior;
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_NUEVO", length = 15, nullable = false)
    private EstadoCuentaEnum estadoNuevo;
    @Column(name = "MOTIVO_CAMBIO", length = 300, nullable = false)
    private String motivoCambio;
    @Column(name = "UUID_USUARIO_CORE", length = 36)
    private String uuidUsuarioCore;
    @Column(name = "FECHA_CAMBIO", nullable = false)
    private LocalDateTime fechaCambio;


    public HistorialEstadoCuenta() {}
    public HistorialEstadoCuenta(Long id) { this.id = id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HistorialEstadoCuenta that)) return false;
        if (this.id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    @Override
    public String toString() {
        return "HistorialEstadoCuenta{" +
                "id=" + id +
                ", estadoAnterior=" + estadoAnterior +
                ", estadoNuevo=" + estadoNuevo +
                "}";
    }
}
