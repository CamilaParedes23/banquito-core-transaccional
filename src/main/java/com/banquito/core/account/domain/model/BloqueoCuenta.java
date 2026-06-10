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
@Table(name = "BLOQUEO_CUENTA")
public class BloqueoCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "UUID_BLOQUEO", length = 36, nullable = false)
    private String uuidBloqueo;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CUENTA_ID", nullable = false)
    private Cuenta cuenta;
    @Column(name = "MONTO_BLOQUEADO", precision = 19, scale = 2, nullable = false)
    private BigDecimal montoBloqueado;
    @Column(name = "MOTIVO", length = 300, nullable = false)
    private String motivo;
    @Column(name = "AUTORIDAD_ORDENANTE", length = 150)
    private String autoridadOrdenante;
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", length = 15, nullable = false)
    private EstadoBloqueoCuentaEnum estado;
    @Column(name = "UUID_USUARIO_CORE", length = 36)
    private String uuidUsuarioCore;
    @Column(name = "FECHA_BLOQUEO", nullable = false)
    private LocalDateTime fechaBloqueo;
    @Column(name = "FECHA_LIBERACION")
    private LocalDateTime fechaLiberacion;
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;


    public BloqueoCuenta() {}
    public BloqueoCuenta(Long id) { this.id = id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BloqueoCuenta that)) return false;
        if (this.id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    @Override
    public String toString() {
        return "BloqueoCuenta{" +
                "id=" + id +
                ", uuidBloqueo=" + uuidBloqueo +
                ", estado=" + estado +
                "}";
    }
}
