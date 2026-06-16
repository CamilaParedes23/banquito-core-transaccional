package com.banquito.core.account.application.service;

import com.banquito.core.account.api.dto.api.AccountTransactionResponse;
import com.banquito.core.account.api.dto.api.P2PTransferRequest;
import com.banquito.core.account.domain.enums.EstadoIdempotenciaEnum;
import com.banquito.core.account.domain.model.RegistroIdempotencia;
import com.banquito.core.account.domain.repository.RegistroIdempotenciaRepository;
import com.banquito.core.account.shared.exception.BusinessException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class P2PIdempotencyService {

    private static final String OPERATION_TYPE = "TRANSFERENCIA_P2P";

    private final RegistroIdempotenciaRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean required;
    private final long ttlHours;

    public P2PIdempotencyService(RegistroIdempotenciaRepository repository,
                                 ObjectMapper objectMapper,
                                 @Value("${banquito.account.idempotency.p2p-required:false}") boolean required,
                                 @Value("${banquito.account.idempotency.ttl-hours:24}") long ttlHours) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.required = required;
        this.ttlHours = ttlHours;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StartResult begin(String actorUuid, String idempotencyKey, P2PTransferRequest request) {
        String normalizedActor = normalizeActor(actorUuid);
        String normalizedKey = normalizeKey(idempotencyKey);

        if (normalizedKey == null) {
            if (required) {
                throw new BusinessException(
                        "IDEMPOTENCY_KEY_REQUIRED",
                        "El encabezado Idempotency-Key es obligatorio para transferencias P2P",
                        HttpStatus.BAD_REQUEST
                );
            }
            return StartResult.withoutIdempotency();
        }

        String requestHash = requestHash(normalizedActor, request);
        LocalDateTime now = LocalDateTime.now();

        int inserted = repository.insertIfAbsent(
                UUID.randomUUID().toString(),
                normalizedActor,
                OPERATION_TYPE,
                normalizedKey,
                requestHash,
                request.correlationId(),
                now,
                now,
                now.plusHours(ttlHours)
        );

        RegistroIdempotencia record = repository.findForUpdate(normalizedActor, OPERATION_TYPE, normalizedKey)
                .orElseThrow(() -> new BusinessException(
                        "IDEMPOTENCY_RECORD_NOT_FOUND",
                        "No fue posible recuperar el registro de idempotencia",
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));

        if (!record.getHashSolicitud().equals(requestHash)) {
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "La clave de idempotencia ya fue utilizada con una solicitud diferente",
                    HttpStatus.CONFLICT
            );
        }

        if (inserted == 1) {
            return StartResult.proceed(record.getId(), normalizedKey);
        }

        if (record.getEstado() == EstadoIdempotenciaEnum.COMPLETADA) {
            return StartResult.replay(record.getId(), normalizedKey, deserialize(record.getRespuestaJson()));
        }

        if (record.getEstado() == EstadoIdempotenciaEnum.EN_PROCESO
                && record.getFechaExpiracion().isAfter(now)) {
            throw new BusinessException(
                    "IDEMPOTENCY_IN_PROGRESS",
                    "Existe una transferencia con la misma clave todavía en procesamiento",
                    HttpStatus.CONFLICT
            );
        }

        record.setEstado(EstadoIdempotenciaEnum.EN_PROCESO);
        record.setHttpStatus(null);
        record.setRespuestaJson(null);
        record.setErrorCodigo(null);
        record.setFechaExpiracion(now.plusHours(ttlHours));
        repository.save(record);
        return StartResult.proceed(record.getId(), normalizedKey);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Long recordId, List<AccountTransactionResponse> response) {
        if (recordId == null) return;
        RegistroIdempotencia record = repository.findById(recordId)
                .orElseThrow(() -> new BusinessException(
                        "IDEMPOTENCY_RECORD_NOT_FOUND",
                        "Registro de idempotencia no encontrado",
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));
        record.setEstado(EstadoIdempotenciaEnum.COMPLETADA);
        record.setHttpStatus(HttpStatus.OK.value());
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

    private String normalizeActor(String actorUuid) {
        if (actorUuid == null || actorUuid.isBlank()) {
            throw new BusinessException(
                    "IDEMPOTENCY_ACTOR_REQUIRED",
                    "No fue posible identificar al actor autenticado",
                    HttpStatus.UNAUTHORIZED
            );
        }
        return actorUuid.trim();
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) return null;
        String key = value.trim();
        if (key.length() > 120) {
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_INVALID",
                    "La clave de idempotencia supera la longitud permitida",
                    HttpStatus.BAD_REQUEST
            );
        }
        try {
            UUID.fromString(key);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key debe contener un UUID válido",
                    HttpStatus.BAD_REQUEST
            );
        }
        return key;
    }

    private String requestHash(String actorUuid, P2PTransferRequest request) {
        BigDecimal amount = request.amount() == null ? null : request.amount().setScale(2, RoundingMode.HALF_UP);
        String canonical = String.join("|",
                actorUuid,
                normalize(request.sourceAccountNumber()),
                normalize(request.targetAccountNumber()),
                amount == null ? "" : amount.toPlainString(),
                normalize(request.description()),
                normalize(request.accountingDate())
        );
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible calcular el hash de idempotencia", exception);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String serialize(List<AccountTransactionResponse> response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible serializar la respuesta idempotente", exception);
        }
    }

    private List<AccountTransactionResponse> deserialize(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<AccountTransactionResponse>>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible recuperar la respuesta idempotente", exception);
        }
    }

    public record StartResult(
            boolean bypass,
            boolean replayed,
            Long recordId,
            String idempotencyKey,
            List<AccountTransactionResponse> response
    ) {
        static StartResult withoutIdempotency() {
            return new StartResult(true, false, null, null, null);
        }

        static StartResult proceed(Long recordId, String key) {
            return new StartResult(false, false, recordId, key, null);
        }

        static StartResult replay(Long recordId, String key, List<AccountTransactionResponse> response) {
            return new StartResult(false, true, recordId, key, response);
        }
    }
}
