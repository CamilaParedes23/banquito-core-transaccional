package com.banquito.core.account.application.service;

import com.banquito.core.account.domain.enums.EstadoBloqueoCuentaEnum;
import com.banquito.core.account.domain.enums.ResultadoAuditoriaAccountEnum;
import com.banquito.core.account.domain.model.BloqueoCuenta;
import com.banquito.core.account.domain.model.Cuenta;
import com.banquito.core.account.domain.repository.BloqueoCuentaRepository;
import com.banquito.core.account.domain.repository.CuentaRepository;
import com.banquito.core.account.shared.exception.BusinessException;
import com.banquito.core.account.shared.tracing.CorrelationIdHolder;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccountBlockExpirationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final CuentaRepository cuentaRepository;
    private final BloqueoCuentaRepository bloqueoRepository;
    private final AuditoriaAccountService auditoriaService;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    public AccountBlockExpirationService(CuentaRepository cuentaRepository,
                                         BloqueoCuentaRepository bloqueoRepository,
                                         AuditoriaAccountService auditoriaService,
                                         OutboxEventService outboxEventService,
                                         ObjectMapper objectMapper) {
        this.cuentaRepository = cuentaRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.auditoriaService = auditoriaService;
        this.outboxEventService = outboxEventService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${banquito.account.blocks.expiration-delay-ms:30000}")
    @Transactional
    public void expireDueBlocks() {
        List<BloqueoCuenta> dueBlocks = bloqueoRepository
                .findByEstadoAndFechaExpiracionLessThanEqual(
                        EstadoBloqueoCuentaEnum.ACTIVO,
                        LocalDateTime.now());
        dueBlocks.stream()
                .map(block -> block.getCuenta().getNumeroCuenta())
                .distinct()
                .forEach(this::expireAccountBlocksInCurrentTransaction);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireForAccount(String accountNumber) {
        expireAccountBlocksInCurrentTransaction(accountNumber);
    }

    private void expireAccountBlocksInCurrentTransaction(String accountNumber) {
        Cuenta account = cuentaRepository.findByNumeroCuenta(accountNumber)
                .orElseThrow(() -> new BusinessException(
                        "ACCOUNT_NOT_FOUND",
                        "Cuenta no encontrada",
                        HttpStatus.NOT_FOUND));
        List<BloqueoCuenta> expiredBlocks = bloqueoRepository
                .findByCuentaAndEstadoAndFechaExpiracionLessThanEqual(
                        account,
                        EstadoBloqueoCuentaEnum.ACTIVO,
                        LocalDateTime.now());
        if (expiredBlocks.isEmpty()) return;

        BigDecimal releasedAmount = expiredBlocks.stream()
                .map(BloqueoCuenta::getMontoBloqueado)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        LocalDateTime releaseTime = LocalDateTime.now();

        expiredBlocks.forEach(block -> {
            block.setEstado(EstadoBloqueoCuentaEnum.EXPIRADO);
            block.setFechaLiberacion(releaseTime);
        });
        bloqueoRepository.saveAll(expiredBlocks);

        BigDecimal retained = account.getMontoRetenido() == null
                ? ZERO
                : account.getMontoRetenido().subtract(releasedAmount).max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);
        account.setMontoRetenido(retained);
        account.setSaldoDisponible(account.getSaldoContable().subtract(retained)
                .setScale(2, RoundingMode.HALF_UP));
        account.setFechaActualizacion(releaseTime);
        cuentaRepository.save(account);

        for (BloqueoCuenta block : expiredBlocks) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("action", "EXPIRED");
            detail.put("blockUuid", block.getUuidBloqueo());
            detail.put("accountNumber", account.getNumeroCuenta());
            detail.put("amount", block.getMontoBloqueado());
            detail.put("status", block.getEstado().name());
            detail.put("reason", block.getMotivo());
            detail.put("actorUuid", "SYSTEM");
            detail.put("blockedAt", block.getFechaBloqueo());
            detail.put("expiresAt", block.getFechaExpiracion());
            detail.put("releasedAt", block.getFechaLiberacion());
            String payload = toJson(detail);
            auditoriaService.registrar(
                    "EXPIRE_ACCOUNT_BLOCK",
                    "BLOQUEO_CUENTA",
                    block.getUuidBloqueo(),
                    ResultadoAuditoriaAccountEnum.OK,
                    payload);
            outboxEventService.registrar(
                    "ACCOUNT_BLOCK_EXPIRED",
                    "BLOQUEO_CUENTA",
                    block.getUuidBloqueo(),
                    CorrelationIdHolder.get(),
                    payload);
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible serializar la expiración de bloqueos", exception);
        }
    }
}
