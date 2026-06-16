package com.banquito.core.account.application.service;

import com.banquito.core.account.domain.enums.EstadoOutboxEventEnum;
import com.banquito.core.account.domain.model.OutboxEvent;
import com.banquito.core.account.domain.repository.OutboxEventRepository;
import com.banquito.core.account.shared.tracing.CorrelationIdHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxEventService {

    private final OutboxEventRepository repository;

    public OutboxEventService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    public void registrar(String tipo, String agregadoTipo, String agregadoId, String payload) {
        registrar(tipo, agregadoTipo, agregadoId, CorrelationIdHolder.get(), payload);
    }

    public void registrar(String tipo,
                          String agregadoTipo,
                          String agregadoId,
                          String correlationId,
                          String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setUuidEvento(UUID.randomUUID().toString());
        event.setUuidCorrelacion(resolveCorrelationId(correlationId));
        event.setTipoEvento(tipo);
        event.setAgregadoTipo(agregadoTipo);
        event.setAgregadoId(agregadoId);
        event.setPayloadJson(payload == null ? "{}" : payload);
        event.setEstado(EstadoOutboxEventEnum.PENDIENTE);
        event.setIntentos(0);
        event.setFechaCreacion(LocalDateTime.now());
        repository.save(event);
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> findDispatchable(List<String> eventTypes, int maxAttempts, int batchSize) {
        return repository.findByTipoEventoInAndEstadoInAndIntentosLessThanOrderByFechaCreacionAsc(
                eventTypes,
                List.of(EstadoOutboxEventEnum.PENDIENTE, EstadoOutboxEventEnum.ERROR),
                maxAttempts,
                PageRequest.of(0, Math.max(1, batchSize))
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(Long id) {
        repository.findById(id).ifPresent(event -> {
            event.setEstado(EstadoOutboxEventEnum.PUBLICADO);
            event.setFechaPublicacion(LocalDateTime.now());
            event.setErrorUltimo(null);
            repository.save(event);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markError(Long id, String error, int maxAttempts) {
        repository.findById(id).ifPresent(event -> {
            int attempts = event.getIntentos() == null ? 1 : event.getIntentos() + 1;
            event.setIntentos(attempts);
            event.setEstado(attempts >= maxAttempts
                    ? EstadoOutboxEventEnum.DESCARTADO
                    : EstadoOutboxEventEnum.ERROR);
            event.setErrorUltimo(truncate(error, 1000));
            repository.save(event);
        });
    }

    public void flush() {
        repository.flush();
    }

    private String resolveCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId.trim();
        }
        String holderCorrelationId = CorrelationIdHolder.get();
        return holderCorrelationId == null || holderCorrelationId.isBlank()
                ? UUID.randomUUID().toString()
                : holderCorrelationId.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
