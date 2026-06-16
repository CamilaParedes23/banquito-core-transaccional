package com.banquito.core.account.domain.repository;

import com.banquito.core.account.domain.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface TransaccionCuentaRepository extends JpaRepository<TransaccionCuenta, Long> {

    Optional<TransaccionCuenta> findByUuidTransaccion(String uuidTransaccion);

    /**
     * Serializa cambios sobre una transacción financiera cuando pueden concurrir
     * el reverso operativo y la vinculación asíncrona del comprobante documental.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransaccionCuenta t where t.uuidTransaccion = :uuidTransaccion")
    Optional<TransaccionCuenta> findByUuidTransaccionForUpdate(
            @Param("uuidTransaccion") String uuidTransaccion);

    Optional<TransaccionCuenta> findFirstByUuidCorrelacionOrderByFechaCreacionDesc(String uuidCorrelacion);

    boolean existsByTransaccionReversada(TransaccionCuenta transaccionReversada);

    @EntityGraph(attributePaths = "cuenta")
    List<TransaccionCuenta> findTop20ByCuentaOrderByTimestampTransaccionDesc(Cuenta cuenta);

    @EntityGraph(attributePaths = "cuenta")
    List<TransaccionCuenta> findByCuentaOrderByTimestampTransaccionDesc(Cuenta cuenta);

}
