package com.banquito.core.account.domain.repository;

import com.banquito.core.account.domain.enums.EstadoBloqueoCuentaEnum;
import com.banquito.core.account.domain.model.BloqueoCuenta;
import com.banquito.core.account.domain.model.Cuenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BloqueoCuentaRepository extends JpaRepository<BloqueoCuenta, Long> {

    Optional<BloqueoCuenta> findByUuidBloqueo(String uuidBloqueo);

    Optional<BloqueoCuenta> findByUuidBloqueoAndCuenta(String uuidBloqueo, Cuenta cuenta);

    List<BloqueoCuenta> findByCuentaOrderByFechaBloqueoDesc(Cuenta cuenta);

    List<BloqueoCuenta> findByCuentaAndEstadoOrderByFechaBloqueoDesc(
            Cuenta cuenta,
            EstadoBloqueoCuentaEnum estado);

    List<BloqueoCuenta> findByCuentaAndEstadoAndFechaExpiracionLessThanEqual(
            Cuenta cuenta,
            EstadoBloqueoCuentaEnum estado,
            LocalDateTime fechaExpiracion);

    List<BloqueoCuenta> findByEstadoAndFechaExpiracionLessThanEqual(
            EstadoBloqueoCuentaEnum estado,
            LocalDateTime fechaExpiracion);
}
