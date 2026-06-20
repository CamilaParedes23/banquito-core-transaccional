package com.banquito.core.account.domain.repository;

import com.banquito.core.account.domain.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
import com.banquito.core.account.domain.enums.*;

public interface InstruccionPagoMasivoCoreRepository extends JpaRepository<InstruccionPagoMasivoCore, Long> {

    Optional<InstruccionPagoMasivoCore> findByPaymentLineUuid(String paymentLineUuid);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InstruccionPagoMasivoCore i where i.paymentLineUuid = :paymentLineUuid")
    Optional<InstruccionPagoMasivoCore> findByPaymentLineUuidForUpdate(@Param("paymentLineUuid") String paymentLineUuid);
    List<InstruccionPagoMasivoCore> findByReservaPagoMasivoOrderByFechaCreacionAsc(ReservaPagoMasivo reservaPagoMasivo);

}
