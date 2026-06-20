package com.banquito.core.account.domain.repository;

import com.banquito.core.account.domain.model.Cuenta;
import com.banquito.core.account.domain.model.HistorialEstadoCuenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialEstadoCuentaRepository extends JpaRepository<HistorialEstadoCuenta, Long> {

    List<HistorialEstadoCuenta> findByCuentaOrderByFechaCambioDesc(Cuenta cuenta);
}
