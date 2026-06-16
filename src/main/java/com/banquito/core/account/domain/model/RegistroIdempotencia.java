package com.banquito.core.account.domain.model;

import com.banquito.core.account.domain.enums.EstadoIdempotenciaEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@Entity
@Table(
        name = "REGISTRO_IDEMPOTENCIA",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_IDEMPOTENCIA_ACTOR_OPERACION_CLAVE",
                columnNames = {"ACTOR_UUID", "TIPO_OPERACION", "CLAVE_IDEMPOTENCIA"}
        )
)
public class RegistroIdempotencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "UUID_REGISTRO", length = 36, nullable = false, unique = true)
    private String uuidRegistro;

    @Column(name = "ACTOR_UUID", length = 80, nullable = false)
    private String actorUuid;

    @Column(name = "TIPO_OPERACION", length = 60, nullable = false)
    private String tipoOperacion;

    @Column(name = "CLAVE_IDEMPOTENCIA", length = 120, nullable = false)
    private String claveIdempotencia;

    @Column(name = "HASH_SOLICITUD", length = 64, nullable = false)
    private String hashSolicitud;

    @Column(name = "UUID_CORRELACION", length = 36)
    private String uuidCorrelacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", length = 20, nullable = false)
    private EstadoIdempotenciaEnum estado;

    @Column(name = "HTTP_STATUS")
    private Integer httpStatus;

    @Column(name = "RESPUESTA_JSON", columnDefinition = "json")
    private String respuestaJson;

    @Column(name = "ERROR_CODIGO", length = 80)
    private String errorCodigo;

    @Column(name = "FECHA_CREACION", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "FECHA_ACTUALIZACION", nullable = false)
    private LocalDateTime fechaActualizacion;

    @Column(name = "FECHA_EXPIRACION", nullable = false)
    private LocalDateTime fechaExpiracion;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (fechaCreacion == null) fechaCreacion = now;
        if (fechaActualizacion == null) fechaActualizacion = now;
        if (estado == null) estado = EstadoIdempotenciaEnum.EN_PROCESO;
        if (version == null) version = 0;
    }

    @PreUpdate
    public void preUpdate() {
        fechaActualizacion = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegistroIdempotencia that)) return false;
        if (id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
