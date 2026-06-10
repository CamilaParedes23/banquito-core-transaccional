package com.banquito.core.account.domain.repository;

import com.banquito.core.account.domain.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import com.banquito.core.account.domain.enums.*;

public interface ReservaPagoMasivoRepository extends JpaRepository<ReservaPagoMasivo, Long> {

    Optional<ReservaPagoMasivo> findByUuidReserva(String uuidReserva);
    Optional<ReservaPagoMasivo> findByBatchIdExterno(String batchIdExterno);

}
