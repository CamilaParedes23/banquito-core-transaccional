package com.banquito.core.account.domain.repository;

import com.banquito.core.account.domain.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MovimientoReservaPagoRepository extends JpaRepository<MovimientoReservaPago, Long> {

    Optional<MovimientoReservaPago> findByUuidMovimiento(String uuidMovimiento);

    Optional<MovimientoReservaPago> findByPaymentLineUuid(String paymentLineUuid);

}
