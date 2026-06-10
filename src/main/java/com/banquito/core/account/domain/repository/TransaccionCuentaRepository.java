package com.banquito.core.account.domain.repository;

import com.banquito.core.account.domain.model.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface TransaccionCuentaRepository extends JpaRepository<TransaccionCuenta, Long> {

    Optional<TransaccionCuenta> findByUuidTransaccion(String uuidTransaccion);

    Optional<TransaccionCuenta> findFirstByUuidCorrelacionOrderByFechaCreacionDesc(String uuidCorrelacion);

    @EntityGraph(attributePaths = "cuenta")
    List<TransaccionCuenta> findTop20ByCuentaOrderByTimestampTransaccionDesc(Cuenta cuenta);

    @EntityGraph(attributePaths = "cuenta")
    List<TransaccionCuenta> findByCuentaOrderByTimestampTransaccionDesc(Cuenta cuenta);

}
