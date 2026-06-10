package com.banquito.core.account.domain.repository;

import com.banquito.core.account.domain.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import com.banquito.core.account.domain.enums.*;

public interface InstruccionPagoMasivoCoreRepository extends JpaRepository<InstruccionPagoMasivoCore, Long> {

    Optional<InstruccionPagoMasivoCore> findByPaymentLineUuid(String paymentLineUuid);
    List<InstruccionPagoMasivoCore> findByReservaPagoMasivoOrderByFechaCreacionAsc(ReservaPagoMasivo reservaPagoMasivo);

}
