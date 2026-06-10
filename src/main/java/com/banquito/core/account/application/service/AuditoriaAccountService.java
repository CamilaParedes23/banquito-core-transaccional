package com.banquito.core.account.application.service;

import com.banquito.core.account.domain.enums.ResultadoAuditoriaAccountEnum;
import com.banquito.core.account.domain.model.AuditoriaAccountEvento;
import com.banquito.core.account.domain.repository.AuditoriaAccountEventoRepository;
import com.banquito.core.account.shared.tracing.CorrelationIdHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class AuditoriaAccountService {
    private final AuditoriaAccountEventoRepository repository;
    public AuditoriaAccountService(AuditoriaAccountEventoRepository repository) { this.repository = repository; }
    public void registrar(String accion, String entidad, String entidadId, ResultadoAuditoriaAccountEnum resultado, String detalleJson) {
        AuditoriaAccountEvento e = new AuditoriaAccountEvento();
        e.setUuidCorrelacion(CorrelationIdHolder.get()); e.setModulo("ACCOUNT"); e.setAccion(accion); e.setEntidad(entidad); e.setEntidadId(entidadId); e.setResultado(resultado); e.setCanalOrigen("ACCOUNT_SERVICE"); e.setDetalleJson(detalleJson); e.setFechaEvento(LocalDateTime.now());
        repository.save(e);
    }
}
