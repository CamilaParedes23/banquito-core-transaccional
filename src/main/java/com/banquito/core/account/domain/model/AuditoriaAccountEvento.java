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
@Table(name = "AUDITORIA_ACCOUNT_EVENTO")
public class AuditoriaAccountEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "UUID_CORRELACION", length = 36)
    private String uuidCorrelacion;
    @Column(name = "UUID_USUARIO", length = 36)
    private String uuidUsuario;
    @Column(name = "UUID_API_CLIENT", length = 36)
    private String uuidApiClient;
    @Column(name = "SCOPE_USADO", length = 120)
    private String scopeUsado;
    @Column(name = "MODULO", length = 60, nullable = false)
    private String modulo;
    @Column(name = "ACCION", length = 80, nullable = false)
    private String accion;
    @Column(name = "ENTIDAD", length = 80, nullable = false)
    private String entidad;
    @Column(name = "ENTIDAD_ID", length = 80)
    private String entidadId;
    @Enumerated(EnumType.STRING)
    @Column(name = "RESULTADO", length = 15, nullable = false)
    private ResultadoAuditoriaAccountEnum resultado;
    @Column(name = "CANAL_ORIGEN", length = 30)
    private String canalOrigen;
    @Column(name = "IP_ORIGEN", length = 45)
    private String ipOrigen;
    @Column(name = "DETALLE_JSON", columnDefinition = "json")
    private String detalleJson;
    @Column(name = "FECHA_EVENTO", nullable = false)
    private LocalDateTime fechaEvento;


    public AuditoriaAccountEvento() {}
    public AuditoriaAccountEvento(Long id) { this.id = id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditoriaAccountEvento that)) return false;
        if (this.id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    @Override
    public String toString() {
        return "AuditoriaAccountEvento{" +
                "id=" + id +
                ", accion=" + accion +
                ", resultado=" + resultado +
                "}";
    }
}
