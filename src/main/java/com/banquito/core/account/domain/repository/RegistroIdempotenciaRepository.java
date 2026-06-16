package com.banquito.core.account.domain.repository;

import com.banquito.core.account.domain.model.RegistroIdempotencia;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RegistroIdempotenciaRepository extends JpaRepository<RegistroIdempotencia, Long> {

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO REGISTRO_IDEMPOTENCIA
            (UUID_REGISTRO, ACTOR_UUID, TIPO_OPERACION, CLAVE_IDEMPOTENCIA,
             HASH_SOLICITUD, UUID_CORRELACION, ESTADO, FECHA_CREACION,
             FECHA_ACTUALIZACION, FECHA_EXPIRACION, VERSION)
            VALUES
            (:uuidRegistro, :actorUuid, :tipoOperacion, :claveIdempotencia,
             :hashSolicitud, :uuidCorrelacion, 'EN_PROCESO', :fechaCreacion,
             :fechaActualizacion, :fechaExpiracion, 0)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("uuidRegistro") String uuidRegistro,
                       @Param("actorUuid") String actorUuid,
                       @Param("tipoOperacion") String tipoOperacion,
                       @Param("claveIdempotencia") String claveIdempotencia,
                       @Param("hashSolicitud") String hashSolicitud,
                       @Param("uuidCorrelacion") String uuidCorrelacion,
                       @Param("fechaCreacion") LocalDateTime fechaCreacion,
                       @Param("fechaActualizacion") LocalDateTime fechaActualizacion,
                       @Param("fechaExpiracion") LocalDateTime fechaExpiracion);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from RegistroIdempotencia r
            where r.actorUuid = :actorUuid
              and r.tipoOperacion = :tipoOperacion
              and r.claveIdempotencia = :claveIdempotencia
            """)
    Optional<RegistroIdempotencia> findForUpdate(@Param("actorUuid") String actorUuid,
                                                  @Param("tipoOperacion") String tipoOperacion,
                                                  @Param("claveIdempotencia") String claveIdempotencia);
}
