package com.banquito.core.account.domain.repository;

import com.banquito.core.account.domain.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import com.banquito.core.account.domain.enums.*;

public interface BloqueoCuentaRepository extends JpaRepository<BloqueoCuenta, Long> {

    Optional<BloqueoCuenta> findByUuidBloqueo(String uuidBloqueo);
    List<BloqueoCuenta> findByCuentaAndEstado(Cuenta cuenta, EstadoBloqueoCuentaEnum estado);

}
