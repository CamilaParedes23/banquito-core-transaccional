package com.banquito.core.account.domain.repository;

import com.banquito.core.account.domain.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
import com.banquito.core.account.domain.enums.*;

public interface ReservaPagoMasivoRepository extends JpaRepository<ReservaPagoMasivo, Long> {

    Optional<ReservaPagoMasivo> findByUuidReserva(String uuidReserva);
    Optional<ReservaPagoMasivo> findByBatchIdExterno(String batchIdExterno);
    Optional<ReservaPagoMasivo> findByUuidCorrelacion(String uuidCorrelacion);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservaPagoMasivo r where r.uuidReserva = :uuidReserva")
    Optional<ReservaPagoMasivo> findByUuidReservaForUpdate(@Param("uuidReserva") String uuidReserva);

}
