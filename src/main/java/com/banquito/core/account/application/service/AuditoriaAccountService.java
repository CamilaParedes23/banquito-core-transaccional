package com.banquito.core.account.application.service;

import com.banquito.core.account.domain.enums.ResultadoAuditoriaAccountEnum;
import com.banquito.core.account.domain.model.AuditoriaAccountEvento;
import com.banquito.core.account.domain.repository.AuditoriaAccountEventoRepository;
import com.banquito.core.account.shared.tracing.CorrelationIdHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuditoriaAccountService {
    private final AuditoriaAccountEventoRepository repository;
    public AuditoriaAccountService(AuditoriaAccountEventoRepository repository) { this.repository = repository; }
    public void registrar(String accion, String entidad, String entidadId, ResultadoAuditoriaAccountEnum resultado, String detalleJson) {
        registrar(CorrelationIdHolder.get(), accion, entidad, entidadId, resultado, detalleJson);
    }
    public void registrar(String correlationId, String accion, String entidad, String entidadId, ResultadoAuditoriaAccountEnum resultado, String detalleJson) {
        AuditoriaAccountEvento e = new AuditoriaAccountEvento();
        e.setUuidCorrelacion(resolveCorrelationId(correlationId)); e.setModulo("ACCOUNT"); e.setAccion(accion); e.setEntidad(entidad); e.setEntidadId(entidadId); e.setResultado(resultado); e.setCanalOrigen("ACCOUNT_SERVICE"); e.setDetalleJson(detalleJson); e.setFechaEvento(LocalDateTime.now());
        repository.save(e);
    }
    private String resolveCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) return correlationId.trim();
        String holder = CorrelationIdHolder.get();
        return holder == null || holder.isBlank() ? UUID.randomUUID().toString() : holder.trim();
    }
}
