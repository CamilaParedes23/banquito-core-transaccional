package com.banquito.core.account.application.service;

import com.banquito.core.account.domain.enums.EstadoOutboxEventEnum;
import com.banquito.core.account.domain.model.OutboxEvent;
import com.banquito.core.account.domain.repository.OutboxEventRepository;
import com.banquito.core.account.shared.tracing.CorrelationIdHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OutboxEventService {
    private final OutboxEventRepository repository;
    public OutboxEventService(OutboxEventRepository repository) { this.repository = repository; }
    public void registrar(String tipo, String agregadoTipo, String agregadoId, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.setUuidEvento(UUID.randomUUID().toString()); e.setUuidCorrelacion(CorrelationIdHolder.get()); e.setTipoEvento(tipo); e.setAgregadoTipo(agregadoTipo); e.setAgregadoId(agregadoId); e.setPayloadJson(payload == null ? "{}" : payload); e.setEstado(EstadoOutboxEventEnum.PENDIENTE); e.setIntentos(0); e.setFechaCreacion(LocalDateTime.now());
        repository.save(e);
    }

    public void flush() {
        repository.flush();
    }
}
