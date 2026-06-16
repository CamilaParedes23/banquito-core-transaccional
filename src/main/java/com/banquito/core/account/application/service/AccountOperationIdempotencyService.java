package com.banquito.core.account.application.service;

import com.banquito.core.account.domain.enums.EstadoIdempotenciaEnum;
import com.banquito.core.account.domain.model.RegistroIdempotencia;
import com.banquito.core.account.domain.repository.RegistroIdempotenciaRepository;
import com.banquito.core.account.shared.exception.BusinessException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AccountOperationIdempotencyService {

    private final RegistroIdempotenciaRepository repository;
    private final ObjectMapper objectMapper;
    private final long ttlHours;

    public AccountOperationIdempotencyService(RegistroIdempotenciaRepository repository,
                                              ObjectMapper objectMapper,
                                              @Value("${banquito.account.idempotency.ttl-hours:24}") long ttlHours) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.ttlHours = ttlHours;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StartResult begin(String actorUuid, String operationType, String idempotencyKey,
                             String correlationId, Object request) {
        String normalizedKey = normalizeKey(idempotencyKey);
        if (normalizedKey == null) {
            return StartResult.withoutIdempotency();
        }
        String normalizedActor = normalizeActor(actorUuid);
        String normalizedOperation = normalizeOperation(operationType);
        String requestHash = requestHash(normalizedActor, normalizedOperation, request);
        LocalDateTime now = LocalDateTime.now();

        int inserted = repository.insertIfAbsent(
                UUID.randomUUID().toString(),
                normalizedActor,
                normalizedOperation,
                normalizedKey,
                requestHash,
                correlationId,
                now,
                now,
                now.plusHours(ttlHours)
        );

        RegistroIdempotencia record = repository.findForUpdate(normalizedActor, normalizedOperation, normalizedKey)
                .orElseThrow(() -> new BusinessException(
                        "IDEMPOTENCY_RECORD_NOT_FOUND",
                        "No fue posible recuperar el registro de idempotencia",
                        HttpStatus.INTERNAL_SERVER_ERROR));

        if (!record.getHashSolicitud().equals(requestHash)) {
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "La clave de idempotencia ya fue utilizada con una solicitud diferente",
                    HttpStatus.CONFLICT);
        }

        if (inserted == 1) {
            return StartResult.proceed(record.getId());
        }
        if (record.getEstado() == EstadoIdempotenciaEnum.COMPLETADA) {
            return StartResult.replay(record.getId(), record.getHttpStatus(), record.getRespuestaJson());
        }
        if (record.getEstado() == EstadoIdempotenciaEnum.EN_PROCESO
                && record.getFechaExpiracion().isAfter(now)) {
            throw new BusinessException(
                    "IDEMPOTENCY_IN_PROGRESS",
                    "Existe una operación con la misma clave todavía en procesamiento",
                    HttpStatus.CONFLICT);
        }

        record.setEstado(EstadoIdempotenciaEnum.EN_PROCESO);
        record.setHttpStatus(null);
        record.setRespuestaJson(null);
        record.setErrorCodigo(null);
        record.setUuidCorrelacion(correlationId);
        record.setFechaExpiracion(now.plusHours(ttlHours));
        repository.save(record);
        return StartResult.proceed(record.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Long recordId, int httpStatus, Object response) {
        if (recordId == null) return;
        RegistroIdempotencia record = repository.findById(recordId)
                .orElseThrow(() -> new BusinessException(
                        "IDEMPOTENCY_RECORD_NOT_FOUND",
                        "Registro de idempotencia no encontrado",
                        HttpStatus.INTERNAL_SERVER_ERROR));
        record.setEstado(EstadoIdempotenciaEnum.COMPLETADA);
        record.setHttpStatus(httpStatus);
        record.setRespuestaJson(serialize(response));
        record.setErrorCodigo(null);
        repository.save(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long recordId, RuntimeException exception) {
        if (recordId == null) return;
        repository.findById(recordId).ifPresent(record -> {
            record.setEstado(EstadoIdempotenciaEnum.FALLIDA);
            record.setErrorCodigo(exception instanceof BusinessException businessException
                    ? businessException.getCode()
                    : exception.getClass().getSimpleName());
            repository.save(record);
        });
    }

    public <T> T deserialize(String json, Class<T> responseType) {
        try {
            return objectMapper.readValue(json, responseType);
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible recuperar la respuesta idempotente", exception);
        }
    }

    private String serialize(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible serializar la respuesta idempotente", exception);
        }
    }

    private String requestHash(String actorUuid, String operationType, Object request) {
        try {
            String canonical = actorUuid + "|" + operationType + "|" + objectMapper.writeValueAsString(request);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible calcular el hash de idempotencia", exception);
        }
    }

    private String normalizeActor(String actorUuid) {
        if (actorUuid == null || actorUuid.isBlank()) {
            throw new BusinessException("IDEMPOTENCY_ACTOR_REQUIRED", "No fue posible identificar al actor autenticado", HttpStatus.UNAUTHORIZED);
        }
        return actorUuid.trim();
    }

    private String normalizeOperation(String operationType) {
        if (operationType == null || operationType.isBlank()) {
            throw new IllegalArgumentException("operationType es obligatorio");
        }
        return operationType.trim();
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) return null;
        String key = value.trim();
        if (key.length() > 120) {
            throw new BusinessException("IDEMPOTENCY_KEY_INVALID", "La clave de idempotencia supera la longitud permitida", HttpStatus.BAD_REQUEST);
        }
        try {
            UUID.fromString(key);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("IDEMPOTENCY_KEY_INVALID", "Idempotency-Key debe contener un UUID válido", HttpStatus.BAD_REQUEST);
        }
        return key;
    }

    public record StartResult(boolean bypass, boolean replayed, Long recordId, Integer httpStatus, String responseJson) {
        static StartResult withoutIdempotency() { return new StartResult(true, false, null, null, null); }
        static StartResult proceed(Long recordId) { return new StartResult(false, false, recordId, null, null); }
        static StartResult replay(Long recordId, Integer httpStatus, String responseJson) {
            return new StartResult(false, true, recordId, httpStatus, responseJson);
        }
    }
}
