package com.banquito.core.account.application.service;

import com.banquito.core.account.api.dto.api.ConsumeReservationRequest;
import com.banquito.core.account.api.dto.api.FeeChargeRequest;
import com.banquito.core.account.api.dto.api.MassPaymentInstructionResponse;
import com.banquito.core.account.api.dto.api.ReservationRequest;
import com.banquito.core.account.api.dto.api.ReservationResponse;
import com.banquito.core.account.api.dto.internal.MassPaymentOperationResult;
import com.banquito.core.account.domain.enums.CanalOrigenCuentaEnum;
import com.banquito.core.account.domain.enums.CanalOrigenReservaEnum;
import com.banquito.core.account.domain.enums.EstadoCuentaEnum;
import com.banquito.core.account.domain.enums.EstadoInstruccionPagoMasivoEnum;
import com.banquito.core.account.domain.enums.EstadoMovimientoReservaEnum;
import com.banquito.core.account.domain.enums.EstadoReservaPagoMasivoEnum;
import com.banquito.core.account.domain.enums.EstadoTransaccionCuentaEnum;
import com.banquito.core.account.domain.enums.ResultadoAuditoriaAccountEnum;
import com.banquito.core.account.domain.enums.TipoDestinoPagoMasivoEnum;
import com.banquito.core.account.domain.enums.TipoMovimientoCuentaEnum;
import com.banquito.core.account.domain.enums.TipoMovimientoReservaEnum;
import com.banquito.core.account.domain.model.Cuenta;
import com.banquito.core.account.domain.model.InstruccionPagoMasivoCore;
import com.banquito.core.account.domain.model.MovimientoReservaPago;
import com.banquito.core.account.domain.model.ReservaPagoMasivo;
import com.banquito.core.account.domain.model.TransaccionCuenta;
import com.banquito.core.account.domain.repository.CuentaRepository;
import com.banquito.core.account.domain.repository.InstruccionPagoMasivoCoreRepository;
import com.banquito.core.account.domain.repository.MovimientoReservaPagoRepository;
import com.banquito.core.account.domain.repository.ReservaPagoMasivoRepository;
import com.banquito.core.account.domain.repository.TransaccionCuentaRepository;
import com.banquito.core.account.infrastructure.grpc.client.AccountingGrpcClient;
import com.banquito.core.account.infrastructure.grpc.client.AdminGrpcClient;
import com.banquito.core.account.infrastructure.grpc.client.CustomerGrpcClient;
import com.banquito.core.account.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class MassPaymentService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private final CuentaRepository cuentaRepository;
    private final TransaccionCuentaRepository transaccionRepository;
    private final ReservaPagoMasivoRepository reservaRepository;
    private final InstruccionPagoMasivoCoreRepository instruccionRepository;
    private final MovimientoReservaPagoRepository movimientoRepository;
    private final CustomerGrpcClient customerGrpcClient;
    private final AdminGrpcClient adminGrpcClient;
    private final AccountingGrpcClient accountingGrpcClient;
    private final AccountBlockExpirationService blockExpirationService;
    private final AuditoriaAccountService auditoriaService;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    public MassPaymentService(CuentaRepository cuentaRepository,
                              TransaccionCuentaRepository transaccionRepository,
                              ReservaPagoMasivoRepository reservaRepository,
                              InstruccionPagoMasivoCoreRepository instruccionRepository,
                              MovimientoReservaPagoRepository movimientoRepository,
                              CustomerGrpcClient customerGrpcClient,
                              AdminGrpcClient adminGrpcClient,
                              AccountingGrpcClient accountingGrpcClient,
                              AccountBlockExpirationService blockExpirationService,
                              AuditoriaAccountService auditoriaService,
                              OutboxEventService outboxEventService,
                              ObjectMapper objectMapper) {
        this.cuentaRepository = cuentaRepository;
        this.transaccionRepository = transaccionRepository;
        this.reservaRepository = reservaRepository;
        this.instruccionRepository = instruccionRepository;
        this.movimientoRepository = movimientoRepository;
        this.customerGrpcClient = customerGrpcClient;
        this.adminGrpcClient = adminGrpcClient;
        this.accountingGrpcClient = accountingGrpcClient;
        this.blockExpirationService = blockExpirationService;
        this.auditoriaService = auditoriaService;
        this.outboxEventService = outboxEventService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MassPaymentOperationResult<ReservationResponse> createReservation(ReservationRequest request) {
        String batchId = normalizeRequired(request.batchId(), "ACCOUNT_BATCH_ID_REQUIRED", "El identificador del lote es obligatorio");
        String correlationId = normalizeRequired(request.correlationId(), "ACCOUNT_CORRELATION_ID_REQUIRED", "El UUID de correlación es obligatorio");
        BigDecimal totalAmount = positiveMoney(request.totalAmount());
        BigDecimal commissionAmount = money(request.commissionAmount() == null ? ZERO : request.commissionAmount());
        CanalOrigenReservaEnum channel = parseChannel(request.channel());

        Optional<ReservaPagoMasivo> existingByBatch = reservaRepository.findByBatchIdExterno(batchId);
        if (existingByBatch.isPresent()) {
            ReservaPagoMasivo existing = existingByBatch.get();
            LocalDate replayAccountingDate = isBlank(request.accountingDate())
                    ? existing.getFechaContable()
                    : parseExplicitDate(request.accountingDate());
            validateReservationReplay(existing, request, totalAmount, commissionAmount, replayAccountingDate, channel);
            return new MassPaymentOperationResult<>(AccountMapper.toReservation(existing), true);
        }
        LocalDate accountingDate = parseDate(request.accountingDate());
        customerGrpcClient.validateMassPaymentsEnabled(request.companyCustomerUuid());
        blockExpirationService.expireForAccount(request.mainAccountNumber());
        Cuenta mainAccount = findAccountForUpdate(request.mainAccountNumber());
        validateMainAccount(mainAccount, request.companyCustomerUuid());

        Optional<ReservaPagoMasivo> concurrentReservation = reservaRepository.findByBatchIdExterno(batchId);
        if (concurrentReservation.isPresent()) {
            ReservaPagoMasivo existing = concurrentReservation.get();
            validateReservationReplay(existing, request, totalAmount, commissionAmount, accountingDate, channel);
            return new MassPaymentOperationResult<>(AccountMapper.toReservation(existing), true);
        }
        reservaRepository.findByUuidCorrelacion(correlationId).ifPresent(existing -> {
            throw new BusinessException(
                    "ACCOUNT_RESERVATION_CORRELATION_REUSED",
                    "El UUID de correlación ya pertenece a otro lote",
                    HttpStatus.CONFLICT);
        });

        BigDecimal reservedAmount = totalAmount;
        ensureAvailable(mainAccount, reservedAmount);

        ReservaPagoMasivo reservation = new ReservaPagoMasivo();
        reservation.setUuidReserva(UUID.randomUUID().toString());
        reservation.setBatchIdExterno(batchId);
        reservation.setUuidCorrelacion(correlationId);
        reservation.setUuidClienteEmpresa(request.companyCustomerUuid().trim());
        reservation.setCuentaMatriz(mainAccount);
        reservation.setNumeroCuentaMatriz(mainAccount.getNumeroCuenta());
        reservation.setCanalOrigen(channel);
        reservation.setMontoTotalLote(totalAmount);
        reservation.setMontoComision(commissionAmount);
        reservation.setMontoComisionCobrado(ZERO);
        reservation.setComisionLiquidada(commissionAmount.compareTo(ZERO) == 0);
        reservation.setMontoReservado(reservedAmount);
        reservation.setMontoConsumidoOnus(ZERO);
        reservation.setMontoConsumidoOffus(ZERO);
        reservation.setMontoLiberado(ZERO);
        reservation.setEstado(EstadoReservaPagoMasivoEnum.ACTIVA);
        reservation.setFechaContable(accountingDate);
        reservation.setFechaCreacion(LocalDateTime.now());
        reservation.setFechaActualizacion(LocalDateTime.now());
        reservation = reservaRepository.saveAndFlush(reservation);

        TransaccionCuenta fundingTransaction = applyMovement(
                mainAccount,
                TipoMovimientoCuentaEnum.DEBITO,
                reservedAmount,
                "RESERVA_PM",
                batchId,
                accountingDate,
                correlationId,
                deterministicTransactionUuid("MASS_PAYMENT_RESERVATION", batchId),
                reservation,
                null,
                true,
                false);

        String journalUuid = accountingGrpcClient.createJournalEntry(journal(
                correlationId,
                fundingTransaction.getUuidTransaccion(),
                "FONDEO_RESERVA_PAGOS_MASIVOS",
                "Débito global y fondeo del lote " + batchId,
                accountingDate,
                batchId,
                List.of(
                        line("CLIENTES_PASIVO", "DEBITO", reservedAmount, "Débito global cuenta matriz", 1),
                        line("FONDOS_RESERVADOS_PM", "CREDITO", reservedAmount, "Fondeo de reserva del lote", 2))));

        fundingTransaction.setAsientoContableUuid(journalUuid);
        transaccionRepository.saveAndFlush(fundingTransaction);
        reservation.setUuidTransaccionFondeo(fundingTransaction.getUuidTransaccion());
        reservation.setAsientoReservaUuid(journalUuid);
        reservation.setFechaActualizacion(LocalDateTime.now());
        reservation = reservaRepository.saveAndFlush(reservation);

        MovimientoReservaPago movement = registerReservationMovement(
                reservation,
                null,
                fundingTransaction,
                TipoMovimientoReservaEnum.RESERVA_INICIAL,
                reservedAmount,
                accountingDate);
        movement.setAsientoContableUuid(journalUuid);
        movimientoRepository.saveAndFlush(movement);

        Map<String, Object> payload = reservationPayload(reservation);
        auditoriaService.registrar(
                correlationId,
                "CREATE_MASS_PAYMENT_RESERVATION",
                "RESERVA_PAGO_MASIVO",
                reservation.getUuidReserva(),
                ResultadoAuditoriaAccountEnum.OK,
                toJson(payload));
        outboxEventService.registrar(
                "MASS_PAYMENT_RESERVATION_FUNDED",
                "RESERVA_PAGO_MASIVO",
                reservation.getUuidReserva(),
                correlationId,
                toJson(payload));

        return new MassPaymentOperationResult<>(AccountMapper.toReservation(reservation), false);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservation(String reservationUuid) {
        return AccountMapper.toReservation(findReservation(reservationUuid));
    }

    @Transactional(readOnly = true)
    public List<MassPaymentInstructionResponse> listInstructions(String reservationUuid) {
        ReservaPagoMasivo reservation = findReservation(reservationUuid);
        return instruccionRepository.findByReservaPagoMasivoOrderByFechaCreacionAsc(reservation)
                .stream()
                .map(AccountMapper::toMassPaymentInstruction)
                .toList();
    }

    @Transactional(readOnly = true)
    public MassPaymentInstructionResponse getInstruction(String reservationUuid, String paymentLineUuid) {
        InstruccionPagoMasivoCore instruction = instruccionRepository.findByPaymentLineUuid(paymentLineUuid)
                .orElseThrow(() -> notFound("ACCOUNT_PAYMENT_INSTRUCTION_NOT_FOUND", "Instrucción de pago no encontrada"));
        ensureInstructionBelongsToReservation(instruction, reservationUuid);
        return AccountMapper.toMassPaymentInstruction(instruction);
    }

    @Transactional
    public MassPaymentOperationResult<MassPaymentInstructionResponse> consumeReservation(
            String reservationUuid,
            ConsumeReservationRequest request) {
        TipoDestinoPagoMasivoEnum destinationType = parseDestinationType(request.destinationType());
        if (destinationType != TipoDestinoPagoMasivoEnum.ON_US) {
            throw new BusinessException(
                    "ACCOUNT_OFFUS_NOT_SUPPORTED",
                    "El Core solo recibe instrucciones locales On-Us; las instrucciones Off-Us deben ser enrutadas por el Switch",
                    HttpStatus.CONFLICT);
        }
        ReservaPagoMasivo reservation = findReservationForUpdate(reservationUuid);

        Optional<InstruccionPagoMasivoCore> existingInstruction =
                instruccionRepository.findByPaymentLineUuidForUpdate(request.paymentLineUuid());
        if (existingInstruction.isPresent()) {
            InstruccionPagoMasivoCore existing = existingInstruction.get();
            ensureInstructionBelongsToReservation(existing, reservationUuid);
            validateInstructionReplay(existing, request);
            return new MassPaymentOperationResult<>(AccountMapper.toMassPaymentInstruction(existing), true);
        }

        validateConsumableReservation(reservation);
        LocalDate accountingDate = resolveReservationDate(reservation, request.accountingDate());
        BigDecimal amount = positiveMoney(request.amount());
        BigDecimal remainingPaymentAmount = reservation.getMontoTotalLote()
                .subtract(reservation.getMontoConsumidoOnus())
                .subtract(reservation.getMontoConsumidoOffus())
                .setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(remainingPaymentAmount) > 0) {
            throw new BusinessException(
                    "ACCOUNT_RESERVATION_PAYMENT_AMOUNT_EXCEEDED",
                    "La instrucción supera el saldo del lote disponible para pagos",
                    HttpStatus.CONFLICT);
        }

        adminGrpcClient.validateRoutingCode(request.routingCode().trim(), true);
        Cuenta destinationAccount = validateOnUsDestination(request);
        if (destinationAccount != null
                && Objects.equals(destinationAccount.getId(), reservation.getCuentaMatriz().getId())) {
            throw new BusinessException(
                    "ACCOUNT_MASS_PAYMENT_SOURCE_EQUALS_DESTINATION",
                    "La cuenta matriz no puede ser también la cuenta beneficiaria",
                    HttpStatus.CONFLICT);
        }
        // La idempotencia de cada instrucción se controla con PAYMENT_LINE_UUID, que ya es
        // único globalmente. UUID_CORRELACION conserva trazabilidad y puede ser propio de la
        // línea o compartido por las líneas de un mismo lote heredado.
        String lineCorrelationId = isBlank(request.correlationId())
                ? request.paymentLineUuid().trim()
                : request.correlationId().trim();

        InstruccionPagoMasivoCore instruction = new InstruccionPagoMasivoCore();
        instruction.setUuidInstruccion(UUID.randomUUID().toString());
        instruction.setReservaPagoMasivo(reservation);
        instruction.setBatchIdExterno(reservation.getBatchIdExterno());
        instruction.setPaymentLineUuid(request.paymentLineUuid().trim());
        instruction.setUuidCorrelacion(lineCorrelationId);
        instruction.setTipoDestino(destinationType);
        instruction.setRoutingCodeDestino(request.routingCode().trim());
        instruction.setCuentaDestino(destinationAccount);
        instruction.setNumeroCuentaDestino(blankToNull(request.destinationAccountNumber()));
        instruction.setCuentaDestinoExterna(blankToNull(request.externalDestinationAccount()));
        instruction.setIdentificacionBeneficiario(request.beneficiaryIdentification().trim());
        instruction.setNombreBeneficiario(request.beneficiaryName().trim());
        instruction.setEmailBeneficiario(blankToNull(request.beneficiaryEmail()));
        instruction.setConcepto(blankToNull(request.concept()));
        instruction.setMonto(amount);
        instruction.setEstado(EstadoInstruccionPagoMasivoEnum.VALIDADA);
        instruction.setFechaContable(accountingDate);
        instruction.setFechaCreacion(LocalDateTime.now());
        instruction.setFechaActualizacion(LocalDateTime.now());
        instruction = instruccionRepository.saveAndFlush(instruction);

        TransaccionCuenta beneficiaryTransaction = applyMovement(
                destinationAccount,
                TipoMovimientoCuentaEnum.CREDITO,
                amount,
                "PAGO_ONUS",
                request.concept(),
                accountingDate,
                lineCorrelationId,
                deterministicTransactionUuid("MASS_PAYMENT_LINE", instruction.getPaymentLineUuid()),
                reservation,
                instruction,
                true,
                false);
        String receiptNumber = beneficiaryTransaction.getNumeroComprobante();

        // Accounting aplica idempotencia por correlación + tipo de operación.
        // PAYMENT_LINE_UUID es la clave idempotente de la línea; UUID_CORRELACION
        // se conserva aparte para trazabilidad y puede compartirse dentro del lote.
        String journalUuid = accountingGrpcClient.createJournalEntry(journal(
                instruction.getPaymentLineUuid(),
                beneficiaryTransaction.getUuidTransaccion(),
                "PAGO_MASIVO_ONUS",
                "Acreditación On-Us del lote " + reservation.getBatchIdExterno(),
                accountingDate,
                request.paymentLineUuid(),
                List.of(
                        line("FONDOS_RESERVADOS_PM", "DEBITO", amount, "Consumo de fondos reservados", 1),
                        line("CLIENTES_PASIVO", "CREDITO", amount, "Crédito al beneficiario BanQuito", 2))));

        beneficiaryTransaction.setAsientoContableUuid(journalUuid);
        transaccionRepository.saveAndFlush(beneficiaryTransaction);
        instruction.setUuidTransaccionCore(beneficiaryTransaction.getUuidTransaccion());
        instruction.setAsientoContableUuid(journalUuid);
        instruction.setNumeroComprobante(receiptNumber);
        instruction.setEstado(EstadoInstruccionPagoMasivoEnum.EJECUTADA);
        instruction.setFechaActualizacion(LocalDateTime.now());
        instruction = instruccionRepository.saveAndFlush(instruction);

        reservation.setMontoConsumidoOnus(reservation.getMontoConsumidoOnus().add(amount));
        updateReservationState(reservation);
        reservationRepositorySave(reservation);

        MovimientoReservaPago movement = registerReservationMovement(
                reservation,
                instruction,
                beneficiaryTransaction,
                TipoMovimientoReservaEnum.CONSUMO_ONUS,
                amount,
                accountingDate);
        movement.setAsientoContableUuid(journalUuid);
        movimientoRepository.saveAndFlush(movement);

        Map<String, Object> payload = instructionPayload(instruction, reservation);
        auditoriaService.registrar(
                lineCorrelationId,
                "SETTLE_MASS_PAYMENT_ONUS",
                "INSTRUCCION_PAGO_MASIVO_CORE",
                instruction.getPaymentLineUuid(),
                ResultadoAuditoriaAccountEnum.OK,
                toJson(payload));
        registerOnUsCompletedEvent(instruction, reservation, beneficiaryTransaction);

        return new MassPaymentOperationResult<>(AccountMapper.toMassPaymentInstruction(instruction), false);
    }

    @Transactional
    public MassPaymentOperationResult<ReservationResponse> chargeServiceFee(
            String reservationUuid,
            FeeChargeRequest request) {
        ReservaPagoMasivo reservation = findReservationForUpdate(reservationUuid);
        LocalDate accountingDate = resolveReservationDate(reservation, request.accountingDate());
        if (!isBlank(request.correlationId())
                && !reservation.getUuidCorrelacion().equals(request.correlationId().trim())) {
            throw new BusinessException(
                    "ACCOUNT_RESERVATION_CORRELATION_MISMATCH",
                    "La correlación de la comisión no coincide con la correlación del lote",
                    HttpStatus.CONFLICT);
        }

        BigDecimal amount = money(request.amount() == null ? reservation.getMontoComision() : request.amount());
        BigDecimal alreadyCharged = nonNullMoney(reservation.getMontoComisionCobrado());
        if (Boolean.TRUE.equals(reservation.getComisionLiquidada())) {
            if (alreadyCharged.compareTo(amount) == 0) {
                return new MassPaymentOperationResult<>(AccountMapper.toReservation(reservation), true);
            }
            throw new BusinessException(
                    "ACCOUNT_RESERVATION_FEE_ALREADY_CHARGED",
                    "La comisión del lote ya fue liquidada con un valor diferente",
                    HttpStatus.CONFLICT);
        }
        validateConsumableReservation(reservation);
        if (amount.compareTo(reservation.getMontoComision()) > 0) {
            throw new BusinessException(
                    "ACCOUNT_RESERVATION_FEE_EXCEEDS_QUOTE",
                    "La comisión final no puede superar el valor máximo informado al crear la reserva",
                    HttpStatus.CONFLICT);
        }

        String correlationId = reservation.getUuidCorrelacion();
        String journalUuid = null;
        TransaccionCuenta feeTransaction = null;
        BigDecimal netAmount = ZERO;
        BigDecimal ivaAmount = ZERO;

        if (amount.compareTo(ZERO) > 0) {
            Cuenta mainAccount = findAccountByIdForUpdate(reservation.getCuentaMatriz().getId());
            validateMainAccount(mainAccount, reservation.getUuidClienteEmpresa());
            validateMassPaymentFeeOverdraftEligibility(mainAccount, amount);

            feeTransaction = applyMovement(
                    mainAccount,
                    TipoMovimientoCuentaEnum.DEBITO,
                    amount,
                    "COMISION_PM",
                    blankToNull(request.externalReference()),
                    accountingDate,
                    correlationId,
                    deterministicTransactionUuid("MASS_PAYMENT_FEE", reservation.getUuidReserva()),
                    reservation,
                    null,
                    true,
                    true);

            BigDecimal ivaRate = adminGrpcClient.getIvaRate();
            BigDecimal divisor = ONE_HUNDRED.add(ivaRate)
                    .divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
            netAmount = amount.divide(divisor, 2, RoundingMode.HALF_UP);
            ivaAmount = amount.subtract(netAmount).setScale(2, RoundingMode.HALF_UP);
            journalUuid = accountingGrpcClient.createJournalEntry(journal(
                    correlationId,
                    feeTransaction.getUuidTransaccion(),
                    "COMISION_PAGOS_MASIVOS",
                    "Liquidación de comisión del lote " + reservation.getBatchIdExterno(),
                    accountingDate,
                    blankToNull(request.externalReference()),
                    List.of(
                            line("CLIENTES_PASIVO", "DEBITO", amount, "Débito de comisión a cuenta matriz", 1),
                            line("INGRESOS_SERVICIOS_MASIVOS", "CREDITO", netAmount, "Ingreso neto por servicio", 2),
                            line("IVA_RETENIDO", "CREDITO", ivaAmount, "IVA retenido", 3))));
            feeTransaction.setAsientoContableUuid(journalUuid);
            transaccionRepository.saveAndFlush(feeTransaction);
        }

        reservation.setMontoComisionCobrado(amount);
        reservation.setComisionLiquidada(true);
        updateReservationState(reservation);
        reservationRepositorySave(reservation);
        MovimientoReservaPago movement = registerReservationMovement(
                reservation,
                null,
                feeTransaction,
                TipoMovimientoReservaEnum.COMISION,
                amount,
                accountingDate);
        movement.setAsientoContableUuid(journalUuid);
        movimientoRepository.saveAndFlush(movement);

        Map<String, Object> payload = reservationPayload(reservation);
        payload.put("netCommission", netAmount);
        payload.put("ivaAmount", ivaAmount);
        payload.put("journalEntryUuid", journalUuid);
        payload.put("transactionUuid", feeTransaction == null ? null : feeTransaction.getUuidTransaccion());
        auditoriaService.registrar(
                correlationId,
                "CHARGE_MASS_PAYMENT_FEE",
                "RESERVA_PAGO_MASIVO",
                reservation.getUuidReserva(),
                ResultadoAuditoriaAccountEnum.OK,
                toJson(payload));
        outboxEventService.registrar(
                "MASS_PAYMENT_FEE_CHARGED",
                "RESERVA_PAGO_MASIVO",
                reservation.getUuidReserva(),
                correlationId,
                toJson(payload));
        return new MassPaymentOperationResult<>(AccountMapper.toReservation(reservation), false);
    }

    @Transactional
    public MassPaymentOperationResult<ReservationResponse> releaseReservation(String reservationUuid) {
        ReservaPagoMasivo reservation = findReservationForUpdate(reservationUuid);
        if (reservation.getEstado() == EstadoReservaPagoMasivoEnum.LIBERADA
                || reservation.getEstado() == EstadoReservaPagoMasivoEnum.CONSUMIDA_TOTAL) {
            return new MassPaymentOperationResult<>(AccountMapper.toReservation(reservation), true);
        }
        if (reservation.getEstado() == EstadoReservaPagoMasivoEnum.REVERSADA) {
            throw new BusinessException("ACCOUNT_RESERVATION_REVERSED", "La reserva ya fue reversada", HttpStatus.CONFLICT);
        }
        if (!Boolean.TRUE.equals(reservation.getComisionLiquidada())) {
            throw new BusinessException(
                    "ACCOUNT_RESERVATION_FEE_PENDING",
                    "La comisión del lote debe liquidarse antes de liberar los fondos sobrantes",
                    HttpStatus.CONFLICT);
        }
        BigDecimal remaining = remainingReservationAmount(reservation);
        if (remaining.compareTo(ZERO) == 0) {
            reservation.setEstado(EstadoReservaPagoMasivoEnum.CONSUMIDA_TOTAL);
            reservation.setFechaCierre(LocalDateTime.now());
            reservationRepositorySave(reservation);
            registerReservationLifecycleAudit("RELEASE_MASS_PAYMENT_RESERVATION", reservation, null);
            return new MassPaymentOperationResult<>(AccountMapper.toReservation(reservation), false);
        }

        Cuenta mainAccount = findAccountByIdForUpdate(reservation.getCuentaMatriz().getId());
        TransaccionCuenta releaseTransaction = applyMovement(
                mainAccount,
                TipoMovimientoCuentaEnum.CREDITO,
                remaining,
                "LIBERACION_PM",
                "Liberación sobrante lote " + reservation.getBatchIdExterno(),
                reservation.getFechaContable(),
                reservation.getUuidCorrelacion(),
                deterministicTransactionUuid("MASS_PAYMENT_RELEASE", reservation.getUuidReserva()),
                reservation,
                null,
                false,
                false);
        String journalUuid = accountingGrpcClient.createJournalEntry(journal(
                reservation.getUuidCorrelacion(),
                releaseTransaction.getUuidTransaccion(),
                "LIBERACION_RESERVA_PAGOS_MASIVOS",
                "Liberación de sobrante del lote " + reservation.getBatchIdExterno(),
                reservation.getFechaContable(),
                reservation.getBatchIdExterno(),
                List.of(
                        line("FONDOS_RESERVADOS_PM", "DEBITO", remaining, "Disminución de fondos reservados", 1),
                        line("CLIENTES_PASIVO", "CREDITO", remaining, "Reintegro a cuenta matriz", 2))));
        releaseTransaction.setAsientoContableUuid(journalUuid);
        transaccionRepository.saveAndFlush(releaseTransaction);

        reservation.setMontoLiberado(reservation.getMontoLiberado().add(remaining));
        reservation.setEstado(EstadoReservaPagoMasivoEnum.LIBERADA);
        reservation.setFechaCierre(LocalDateTime.now());
        reservationRepositorySave(reservation);
        MovimientoReservaPago movement = registerReservationMovement(
                reservation,
                null,
                releaseTransaction,
                TipoMovimientoReservaEnum.LIBERACION_SOBRANTE,
                remaining,
                reservation.getFechaContable());
        movement.setAsientoContableUuid(journalUuid);
        movimientoRepository.saveAndFlush(movement);

        registerReservationLifecycleAudit("RELEASE_MASS_PAYMENT_RESERVATION", reservation, journalUuid);
        return new MassPaymentOperationResult<>(AccountMapper.toReservation(reservation), false);
    }

    @Transactional
    public MassPaymentOperationResult<ReservationResponse> closeReservation(String reservationUuid) {
        ReservaPagoMasivo reservation = findReservationForUpdate(reservationUuid);
        if (reservation.getEstado() == EstadoReservaPagoMasivoEnum.LIBERADA
                || reservation.getEstado() == EstadoReservaPagoMasivoEnum.CONSUMIDA_TOTAL) {
            return new MassPaymentOperationResult<>(AccountMapper.toReservation(reservation), true);
        }
        if (reservation.getEstado() == EstadoReservaPagoMasivoEnum.REVERSADA) {
            throw new BusinessException("ACCOUNT_RESERVATION_REVERSED", "La reserva ya fue reversada", HttpStatus.CONFLICT);
        }
        if (!Boolean.TRUE.equals(reservation.getComisionLiquidada())) {
            throw new BusinessException(
                    "ACCOUNT_RESERVATION_FEE_PENDING",
                    "La comisión del lote debe liquidarse antes del cierre",
                    HttpStatus.CONFLICT);
        }
        BigDecimal remaining = remainingReservationAmount(reservation);
        if (remaining.compareTo(ZERO) > 0) {
            return releaseReservation(reservationUuid);
        }
        reservation.setEstado(EstadoReservaPagoMasivoEnum.CONSUMIDA_TOTAL);
        reservation.setFechaCierre(LocalDateTime.now());
        reservationRepositorySave(reservation);
        registerReservationLifecycleAudit("CLOSE_MASS_PAYMENT_RESERVATION", reservation, null);
        return new MassPaymentOperationResult<>(AccountMapper.toReservation(reservation), false);
    }

    @Transactional
    public MassPaymentOperationResult<ReservationResponse> reverseReservation(String reservationUuid) {
        ReservaPagoMasivo reservation = findReservationForUpdate(reservationUuid);
        if (reservation.getEstado() == EstadoReservaPagoMasivoEnum.REVERSADA) {
            return new MassPaymentOperationResult<>(AccountMapper.toReservation(reservation), true);
        }
        if (reservation.getEstado() == EstadoReservaPagoMasivoEnum.LIBERADA
                || reservation.getEstado() == EstadoReservaPagoMasivoEnum.CONSUMIDA_TOTAL) {
            throw new BusinessException(
                    "ACCOUNT_RESERVATION_NOT_REVERSIBLE",
                    "Una reserva cerrada no puede reversarse",
                    HttpStatus.CONFLICT);
        }
        if (reservation.getMontoConsumidoOnus().compareTo(ZERO) > 0
                || reservation.getMontoConsumidoOffus().compareTo(ZERO) > 0
                || nonNullMoney(reservation.getMontoComisionCobrado()).compareTo(ZERO) > 0
                || reservation.getMontoLiberado().compareTo(ZERO) > 0) {
            throw new BusinessException(
                    "ACCOUNT_RESERVATION_HAS_SETTLEMENTS",
                    "La reserva contiene liquidaciones y requiere reversos especializados por instrucción",
                    HttpStatus.CONFLICT);
        }

        Cuenta mainAccount = findAccountByIdForUpdate(reservation.getCuentaMatriz().getId());
        TransaccionCuenta reversalTransaction = applyMovement(
                mainAccount,
                TipoMovimientoCuentaEnum.CREDITO,
                reservation.getMontoReservado(),
                "REVERSO_RESERVA_PM",
                "Reverso total lote " + reservation.getBatchIdExterno(),
                reservation.getFechaContable(),
                reservation.getUuidCorrelacion(),
                deterministicTransactionUuid("MASS_PAYMENT_REVERSAL", reservation.getUuidReserva()),
                reservation,
                null,
                false,
                false);
        transaccionRepository.findByUuidTransaccion(reservation.getUuidTransaccionFondeo()).ifPresent(original -> {
            reversalTransaction.setTransaccionReversada(original);
            reversalTransaction.setMotivoReverso("Reverso total de reserva sin consumos");
            original.setEstado(EstadoTransaccionCuentaEnum.REVERSADA);
            transaccionRepository.save(original);
        });

        String journalUuid = accountingGrpcClient.createJournalEntry(journal(
                reservation.getUuidCorrelacion(),
                reversalTransaction.getUuidTransaccion(),
                "REVERSO_FONDEO_RESERVA_PAGOS_MASIVOS",
                "Reverso total del lote " + reservation.getBatchIdExterno(),
                reservation.getFechaContable(),
                reservation.getBatchIdExterno(),
                List.of(
                        line("FONDOS_RESERVADOS_PM", "DEBITO", reservation.getMontoReservado(), "Reverso de fondos reservados", 1),
                        line("CLIENTES_PASIVO", "CREDITO", reservation.getMontoReservado(), "Reintegro total a cuenta matriz", 2))));
        reversalTransaction.setAsientoContableUuid(journalUuid);
        transaccionRepository.saveAndFlush(reversalTransaction);

        reservation.setEstado(EstadoReservaPagoMasivoEnum.REVERSADA);
        reservation.setFechaCierre(LocalDateTime.now());
        reservationRepositorySave(reservation);
        MovimientoReservaPago movement = registerReservationMovement(
                reservation,
                null,
                reversalTransaction,
                TipoMovimientoReservaEnum.REVERSO,
                reservation.getMontoReservado(),
                reservation.getFechaContable());
        movement.setAsientoContableUuid(journalUuid);
        movimientoRepository.saveAndFlush(movement);

        registerReservationLifecycleAudit("REVERSE_MASS_PAYMENT_RESERVATION", reservation, journalUuid);
        return new MassPaymentOperationResult<>(AccountMapper.toReservation(reservation), false);
    }

    private Cuenta validateOnUsDestination(ConsumeReservationRequest request) {
        if (isBlank(request.destinationAccountNumber())) {
            throw new BusinessException("ACCOUNT_DESTINATION_REQUIRED", "La cuenta destino On-Us es obligatoria", HttpStatus.BAD_REQUEST);
        }
        if (!isBlank(request.externalDestinationAccount())) {
            throw new BusinessException("ACCOUNT_EXTERNAL_DESTINATION_NOT_ALLOWED", "Un pago On-Us no admite cuenta externa", HttpStatus.BAD_REQUEST);
        }
        Cuenta destination = findAccountForUpdate(request.destinationAccountNumber());
        if (destination.getEstado() != EstadoCuentaEnum.ACTIVA) {
            throw new BusinessException("ACCOUNT_DESTINATION_NOT_ACTIVE", "La cuenta destino no está activa", HttpStatus.CONFLICT);
        }
        if (!destination.getIdentificacionTitular().equals(request.beneficiaryIdentification().trim())) {
            throw new BusinessException(
                    "ACCOUNT_BENEFICIARY_MISMATCH",
                    "La identificación del beneficiario no corresponde a la cuenta destino",
                    HttpStatus.CONFLICT);
        }
        return destination;
    }

    private TransaccionCuenta applyMovement(Cuenta account,
                                             TipoMovimientoCuentaEnum movementType,
                                             BigDecimal amount,
                                             String subtype,
                                             String externalReference,
                                             LocalDate accountingDate,
                                             String correlationId,
                                             String transactionUuid,
                                             ReservaPagoMasivo reservation,
                                             InstruccionPagoMasivoCore instruction,
                                             boolean requireActive,
                                             boolean allowMassPaymentFeeOverdraft) {
        adminGrpcClient.validateTransactionSubtypeActive(subtype, movementType.name());
        if (requireActive && account.getEstado() != EstadoCuentaEnum.ACTIVA) {
            throw new BusinessException("ACCOUNT_NOT_ACTIVE", "La cuenta no está activa", HttpStatus.CONFLICT);
        }
        if (!requireActive && account.getEstado() == EstadoCuentaEnum.CERRADA) {
            throw new BusinessException("ACCOUNT_CLOSED", "La cuenta cerrada no admite movimientos", HttpStatus.CONFLICT);
        }
        BigDecimal normalizedAmount = positiveMoney(amount);
        if (movementType == TipoMovimientoCuentaEnum.DEBITO) {
            if (allowMassPaymentFeeOverdraft) ensureMassPaymentFeeCapacity(account, normalizedAmount);
            else ensureAvailable(account, normalizedAmount);
        }

        BigDecimal previousBalance = account.getSaldoContable();
        if (movementType == TipoMovimientoCuentaEnum.DEBITO) {
            account.setSaldoContable(account.getSaldoContable().subtract(normalizedAmount));
            account.setSaldoDisponible(account.getSaldoDisponible().subtract(normalizedAmount));
        } else {
            account.setSaldoContable(account.getSaldoContable().add(normalizedAmount));
            account.setSaldoDisponible(account.getSaldoDisponible().add(normalizedAmount));
        }
        account.setFechaActualizacion(LocalDateTime.now());
        cuentaRepository.save(account);

        TransaccionCuenta transaction = new TransaccionCuenta();
        transaction.setUuidTransaccion(transactionUuid);
        transaction.setUuidCorrelacion(correlationId);
        transaction.setCuenta(account);
        transaction.setCodigoSubtipoTransaccion(subtype);
        transaction.setReservaPagoMasivo(reservation);
        transaction.setInstruccionPagoCore(instruction);
        transaction.setFechaContable(accountingDate);
        transaction.setTimestampTransaccion(LocalDateTime.now());
        transaction.setTipoMovimiento(movementType);
        transaction.setMonto(normalizedAmount);
        transaction.setSaldoContableAnterior(previousBalance);
        transaction.setSaldoContableResultante(account.getSaldoContable());
        transaction.setSaldoDisponibleResultante(account.getSaldoDisponible());
        transaction.setEstado(EstadoTransaccionCuentaEnum.APLICADA);
        transaction.setCanalOrigen(CanalOrigenCuentaEnum.SWITCH);
        transaction.setReferenciaExterna(blankToNull(externalReference));
        transaction.setNumeroComprobante("CMP-" + System.currentTimeMillis());
        transaction.setFechaCreacion(LocalDateTime.now());
        return transaccionRepository.saveAndFlush(transaction);
    }

    private MovimientoReservaPago registerReservationMovement(ReservaPagoMasivo reservation,
                                                               InstruccionPagoMasivoCore instruction,
                                                               TransaccionCuenta transaction,
                                                               TipoMovimientoReservaEnum type,
                                                               BigDecimal amount,
                                                               LocalDate accountingDate) {
        MovimientoReservaPago movement = new MovimientoReservaPago();
        movement.setUuidMovimiento(UUID.randomUUID().toString());
        movement.setReservaPagoMasivo(reservation);
        movement.setInstruccionPagoCore(instruction);
        movement.setTransaccionCuenta(transaction);
        movement.setPaymentLineUuid(instruction == null ? null : instruction.getPaymentLineUuid());
        movement.setTipoMovimiento(type);
        movement.setMonto(money(amount));
        movement.setEstado(EstadoMovimientoReservaEnum.APLICADO);
        movement.setFechaContable(accountingDate);
        movement.setFechaCreacion(LocalDateTime.now());
        return movimientoRepository.saveAndFlush(movement);
    }

    private void registerOnUsCompletedEvent(InstruccionPagoMasivoCore instruction,
                                            ReservaPagoMasivo reservation,
                                            TransaccionCuenta transaction) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("correlationId", instruction.getUuidCorrelacion());
        payload.put("batchCorrelationId", reservation.getUuidCorrelacion());
        payload.put("transactionUuid", transaction.getUuidTransaccion());
        payload.put("paymentLineUuid", instruction.getPaymentLineUuid());
        payload.put("reservationUuid", reservation.getUuidReserva());
        payload.put("beneficiaryCustomerUuid", transaction.getCuenta().getUuidCliente());
        payload.put("beneficiaryName", instruction.getNombreBeneficiario());
        payload.put("beneficiaryEmail", instruction.getEmailBeneficiario());
        payload.put("destinationAccountNumber", transaction.getCuenta().getNumeroCuenta());
        payload.put("companyName", reservation.getCuentaMatriz().getNombreTitularReferencia());
        payload.put("amount", transaction.getMonto());
        payload.put("concept", instruction.getConcepto() == null ? "Pago masivo" : instruction.getConcepto());
        payload.put("accountingDate", transaction.getFechaContable().toString());
        payload.put("receiptNumber", transaction.getNumeroComprobante());
        outboxEventService.registrar(
                "ONUS_PAYMENT_COMPLETED",
                "INSTRUCCION_PAGO_MASIVO_CORE",
                instruction.getPaymentLineUuid(),
                instruction.getUuidCorrelacion(),
                toJson(payload));
    }

    private void validateReservationReplay(ReservaPagoMasivo existing,
                                           ReservationRequest request,
                                           BigDecimal totalAmount,
                                           BigDecimal commissionAmount,
                                           LocalDate accountingDate,
                                           CanalOrigenReservaEnum channel) {
        boolean same = Objects.equals(existing.getUuidClienteEmpresa(), request.companyCustomerUuid().trim())
                && Objects.equals(existing.getNumeroCuentaMatriz(), request.mainAccountNumber().trim())
                && existing.getMontoTotalLote().compareTo(totalAmount) == 0
                && existing.getMontoComision().compareTo(commissionAmount) == 0
                && Objects.equals(existing.getUuidCorrelacion(), request.correlationId().trim())
                && Objects.equals(existing.getFechaContable(), accountingDate)
                && existing.getCanalOrigen() == channel;
        if (!same) {
            throw new BusinessException(
                    "ACCOUNT_BATCH_ID_REUSED",
                    "El identificador del lote ya fue utilizado con una solicitud diferente",
                    HttpStatus.CONFLICT);
        }
    }

    private void validateInstructionReplay(InstruccionPagoMasivoCore existing,
                                           ConsumeReservationRequest request) {
        TipoDestinoPagoMasivoEnum destinationType = parseDestinationType(request.destinationType());
        LocalDate requestedAccountingDate = isBlank(request.accountingDate())
                ? existing.getReservaPagoMasivo().getFechaContable()
                : parseExplicitDate(request.accountingDate());
        String requestedCorrelation = isBlank(request.correlationId())
                ? request.paymentLineUuid().trim()
                : request.correlationId().trim();
        boolean same = Objects.equals(existing.getUuidCorrelacion(), requestedCorrelation)
                && existing.getTipoDestino() == destinationType
                && Objects.equals(existing.getRoutingCodeDestino(), request.routingCode().trim())
                && Objects.equals(blankToNull(existing.getNumeroCuentaDestino()), blankToNull(request.destinationAccountNumber()))
                && Objects.equals(blankToNull(existing.getCuentaDestinoExterna()), blankToNull(request.externalDestinationAccount()))
                && Objects.equals(existing.getIdentificacionBeneficiario(), request.beneficiaryIdentification().trim())
                && Objects.equals(existing.getNombreBeneficiario(), request.beneficiaryName().trim())
                && Objects.equals(blankToNull(existing.getEmailBeneficiario()), blankToNull(request.beneficiaryEmail()))
                && Objects.equals(blankToNull(existing.getConcepto()), blankToNull(request.concept()))
                && existing.getMonto().compareTo(money(request.amount())) == 0
                && Objects.equals(existing.getFechaContable(), requestedAccountingDate);
        if (!same) {
            throw new BusinessException(
                    "ACCOUNT_PAYMENT_LINE_PAYLOAD_CONFLICT",
                    "La línea de pago ya fue utilizada con datos diferentes",
                    HttpStatus.CONFLICT);
        }
    }

    private void validateMassPaymentFeeOverdraftEligibility(Cuenta account, BigDecimal amount) {
        if (account.getSaldoDisponible().compareTo(amount) >= 0) return;
        if (!Boolean.TRUE.equals(account.getPermiteSobregiro())) {
            throw new BusinessException(
                    "ACCOUNT_MASS_PAYMENT_FEE_OVERDRAFT_DISABLED",
                    "La cuenta no tiene sobregiro autorizado para cubrir la comisión de pagos masivos",
                    HttpStatus.CONFLICT);
        }
        if (account.getLimiteSobregiro() == null || account.getLimiteSobregiro().compareTo(ZERO) <= 0) {
            throw new BusinessException(
                    "ACCOUNT_MASS_PAYMENT_FEE_OVERDRAFT_LIMIT_REQUIRED",
                    "La cuenta no tiene un límite de sobregiro válido",
                    HttpStatus.CONFLICT);
        }
        var product = adminGrpcClient.getActiveAccountSubtype(account.getCodigoSubtipoCuenta());
        if (!"CORRIENTE".equalsIgnoreCase(product.getBaseType())) {
            throw new BusinessException(
                    "ACCOUNT_MASS_PAYMENT_FEE_OVERDRAFT_ONLY_CHECKING",
                    "El sobregiro para comisión solo puede utilizarse en cuentas corrientes",
                    HttpStatus.CONFLICT);
        }
        ensureMassPaymentFeeCapacity(account, amount);
    }

    private void ensureMassPaymentFeeCapacity(Cuenta account, BigDecimal amount) {
        BigDecimal minimumAllowed = Boolean.TRUE.equals(account.getPermiteSobregiro())
                ? nonNullMoney(account.getLimiteSobregiro()).negate()
                : ZERO;
        BigDecimal resultingAvailable = account.getSaldoDisponible().subtract(amount);
        if (resultingAvailable.compareTo(minimumAllowed) < 0) {
            throw new BusinessException(
                    "ACCOUNT_MASS_PAYMENT_FEE_OVERDRAFT_LIMIT_EXCEEDED",
                    "La comisión supera el saldo disponible y el límite de sobregiro autorizado",
                    HttpStatus.CONFLICT);
        }
    }

    private String deterministicTransactionUuid(String namespace, String key) {
        String source = namespace + ":" + normalizeRequired(
                key,
                "ACCOUNT_TRANSACTION_IDEMPOTENCY_KEY_REQUIRED",
                "No fue posible construir la identidad transaccional");
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void validateMainAccount(Cuenta account, String companyCustomerUuid) {
        if (account.getEstado() != EstadoCuentaEnum.ACTIVA) {
            throw new BusinessException("ACCOUNT_NOT_ACTIVE", "La cuenta matriz no está activa", HttpStatus.CONFLICT);
        }
        if (!Boolean.TRUE.equals(account.getEsCuentaMatrizPagos())) {
            throw new BusinessException(
                    "ACCOUNT_MASS_PAYMENTS_ACCOUNT_NOT_ENABLED",
                    "La cuenta no está habilitada como matriz de pagos masivos",
                    HttpStatus.CONFLICT);
        }
        if (!Objects.equals(account.getUuidCliente(), companyCustomerUuid.trim())) {
            throw new BusinessException(
                    "ACCOUNT_RESERVATION_CUSTOMER_MISMATCH",
                    "La cuenta matriz no pertenece a la empresa indicada",
                    HttpStatus.CONFLICT);
        }
    }

    private void validateConsumableReservation(ReservaPagoMasivo reservation) {
        if (reservation.getEstado() != EstadoReservaPagoMasivoEnum.ACTIVA
                && reservation.getEstado() != EstadoReservaPagoMasivoEnum.CONSUMIDA_PARCIAL) {
            throw new BusinessException(
                    "ACCOUNT_RESERVATION_NOT_CONSUMABLE",
                    "La reserva no se encuentra disponible para liquidación",
                    HttpStatus.CONFLICT);
        }
    }

    private void ensureInstructionBelongsToReservation(InstruccionPagoMasivoCore instruction,
                                                       String reservationUuid) {
        if (!instruction.getReservaPagoMasivo().getUuidReserva().equals(reservationUuid)) {
            throw new BusinessException(
                    "ACCOUNT_PAYMENT_LINE_ALREADY_USED",
                    "La línea de pago pertenece a otra reserva",
                    HttpStatus.CONFLICT);
        }
    }

    private LocalDate resolveReservationDate(ReservaPagoMasivo reservation, String requestedDate) {
        if (isBlank(requestedDate)) return reservation.getFechaContable();
        LocalDate date = parseExplicitDate(requestedDate);
        if (!date.equals(reservation.getFechaContable())) {
            throw new BusinessException(
                    "ACCOUNT_RESERVATION_ACCOUNTING_DATE_MISMATCH",
                    "La fecha contable de la instrucción no coincide con la reserva",
                    HttpStatus.CONFLICT);
        }
        return date;
    }

    private void updateReservationState(ReservaPagoMasivo reservation) {
        BigDecimal allocated = reservation.getMontoConsumidoOnus()
                .add(reservation.getMontoConsumidoOffus())
                .add(reservation.getMontoLiberado());
        boolean reservationFullyAllocated = allocated.compareTo(reservation.getMontoReservado()) >= 0;
        boolean commissionSettled = Boolean.TRUE.equals(reservation.getComisionLiquidada());
        if (reservationFullyAllocated && commissionSettled) {
            reservation.setEstado(EstadoReservaPagoMasivoEnum.CONSUMIDA_TOTAL);
            reservation.setFechaCierre(LocalDateTime.now());
        } else if (allocated.compareTo(ZERO) > 0 || commissionSettled) {
            reservation.setEstado(EstadoReservaPagoMasivoEnum.CONSUMIDA_PARCIAL);
            reservation.setFechaCierre(null);
        } else {
            reservation.setEstado(EstadoReservaPagoMasivoEnum.ACTIVA);
            reservation.setFechaCierre(null);
        }
        reservation.setFechaActualizacion(LocalDateTime.now());
    }

    private BigDecimal remainingReservationAmount(ReservaPagoMasivo reservation) {
        BigDecimal remaining = reservation.getMontoReservado()
                .subtract(reservation.getMontoConsumidoOnus())
                .subtract(reservation.getMontoConsumidoOffus())
                .subtract(reservation.getMontoLiberado())
                .setScale(2, RoundingMode.HALF_UP);
        return remaining.max(ZERO);
    }

    private void reservationRepositorySave(ReservaPagoMasivo reservation) {
        reservation.setFechaActualizacion(LocalDateTime.now());
        reservaRepository.saveAndFlush(reservation);
    }

    private void registerReservationLifecycleAudit(String action,
                                                   ReservaPagoMasivo reservation,
                                                   String journalUuid) {
        Map<String, Object> payload = reservationPayload(reservation);
        payload.put("journalEntryUuid", journalUuid);
        auditoriaService.registrar(
                reservation.getUuidCorrelacion(),
                action,
                "RESERVA_PAGO_MASIVO",
                reservation.getUuidReserva(),
                ResultadoAuditoriaAccountEnum.OK,
                toJson(payload));
        outboxEventService.registrar(
                action,
                "RESERVA_PAGO_MASIVO",
                reservation.getUuidReserva(),
                reservation.getUuidCorrelacion(),
                toJson(payload));
    }

    private Map<String, Object> reservationPayload(ReservaPagoMasivo reservation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationUuid", reservation.getUuidReserva());
        payload.put("batchId", reservation.getBatchIdExterno());
        payload.put("correlationId", reservation.getUuidCorrelacion());
        payload.put("companyCustomerUuid", reservation.getUuidClienteEmpresa());
        payload.put("mainAccountNumber", reservation.getNumeroCuentaMatriz());
        payload.put("status", reservation.getEstado().name());
        payload.put("totalBatchAmount", reservation.getMontoTotalLote());
        payload.put("commissionAmount", reservation.getMontoComision());
        payload.put("chargedCommission", nonNullMoney(reservation.getMontoComisionCobrado()));
        payload.put("commissionSettled", Boolean.TRUE.equals(reservation.getComisionLiquidada()));
        payload.put("reservedAmount", reservation.getMontoReservado());
        payload.put("consumedOnUs", reservation.getMontoConsumidoOnus());
        payload.put("consumedOffUs", reservation.getMontoConsumidoOffus());
        payload.put("releasedAmount", reservation.getMontoLiberado());
        payload.put("remainingAmount", remainingReservationAmount(reservation));
        payload.put("accountingDate", reservation.getFechaContable().toString());
        payload.put("fundingTransactionUuid", reservation.getUuidTransaccionFondeo());
        payload.put("fundingJournalEntryUuid", reservation.getAsientoReservaUuid());
        return payload;
    }

    private Map<String, Object> instructionPayload(InstruccionPagoMasivoCore instruction,
                                                   ReservaPagoMasivo reservation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instructionUuid", instruction.getUuidInstruccion());
        payload.put("reservationUuid", reservation.getUuidReserva());
        payload.put("batchId", reservation.getBatchIdExterno());
        payload.put("batchCorrelationId", reservation.getUuidCorrelacion());
        payload.put("paymentLineUuid", instruction.getPaymentLineUuid());
        payload.put("correlationId", instruction.getUuidCorrelacion());
        payload.put("destinationType", instruction.getTipoDestino().name());
        payload.put("routingCode", instruction.getRoutingCodeDestino());
        payload.put("destinationAccountNumber", instruction.getNumeroCuentaDestino());
        payload.put("externalDestinationAccount", instruction.getCuentaDestinoExterna());
        payload.put("beneficiaryIdentification", instruction.getIdentificacionBeneficiario());
        payload.put("amount", instruction.getMonto());
        payload.put("status", instruction.getEstado().name());
        payload.put("transactionUuid", instruction.getUuidTransaccionCore());
        payload.put("journalEntryUuid", instruction.getAsientoContableUuid());
        payload.put("receiptNumber", instruction.getNumeroComprobante());
        return payload;
    }

    private Map<String, Object> journal(String correlationId,
                                        String transactionUuid,
                                        String operationType,
                                        String description,
                                        LocalDate accountingDate,
                                        String externalReference,
                                        List<Map<String, Object>> lines) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("correlationId", correlationId);
        payload.put("transactionUuid", transactionUuid);
        payload.put("originContext", "SWITCH");
        payload.put("operationType", operationType);
        payload.put("description", description);
        payload.put("accountingDate", accountingDate.toString());
        payload.put("externalReference", externalReference);
        payload.put("lines", lines);
        return payload;
    }

    private Map<String, Object> line(String institutionalAccountCode,
                                     String movementType,
                                     BigDecimal amount,
                                     String reference,
                                     int order) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("accountingCode", null);
        line.put("institutionalAccountCode", institutionalAccountCode);
        line.put("movementType", movementType);
        line.put("amount", amount);
        line.put("reference", reference);
        line.put("lineOrder", order);
        return line;
    }

    private Cuenta findAccountForUpdate(String accountNumber) {
        String normalized = normalizeRequired(accountNumber, "ACCOUNT_NUMBER_REQUIRED", "El número de cuenta es obligatorio");
        return cuentaRepository.findByNumeroCuentaForUpdate(normalized)
                .orElseThrow(() -> notFound("ACCOUNT_NOT_FOUND", "Cuenta no encontrada"));
    }

    private Cuenta findAccountByIdForUpdate(Long accountId) {
        return cuentaRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> notFound("ACCOUNT_NOT_FOUND", "Cuenta no encontrada"));
    }

    private ReservaPagoMasivo findReservation(String reservationUuid) {
        return reservaRepository.findByUuidReserva(reservationUuid)
                .orElseThrow(() -> notFound("ACCOUNT_RESERVATION_NOT_FOUND", "Reserva no encontrada"));
    }

    private ReservaPagoMasivo findReservationForUpdate(String reservationUuid) {
        return reservaRepository.findByUuidReservaForUpdate(reservationUuid)
                .orElseThrow(() -> notFound("ACCOUNT_RESERVATION_NOT_FOUND", "Reserva no encontrada"));
    }

    private void ensureAvailable(Cuenta account, BigDecimal amount) {
        if (account.getSaldoDisponible().compareTo(amount) < 0) {
            throw new BusinessException("ACCOUNT_INSUFFICIENT_FUNDS", "Saldo disponible insuficiente", HttpStatus.CONFLICT);
        }
    }

    private LocalDate parseDate(String value) {
        return isBlank(value) ? accountingGrpcClient.resolveOperationAccountingDate() : parseExplicitDate(value);
    }

    private LocalDate parseExplicitDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    "ACCOUNT_INVALID_ACCOUNTING_DATE",
                    "La fecha contable debe ser una fecha válida con formato yyyy-MM-dd",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private CanalOrigenReservaEnum parseChannel(String value) {
        try {
            return CanalOrigenReservaEnum.valueOf(
                    isBlank(value) ? CanalOrigenReservaEnum.SWITCH_API.name() : value.trim());
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    "ACCOUNT_INVALID_RESERVATION_CHANNEL",
                    "El canal de origen de la reserva no es válido",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private TipoDestinoPagoMasivoEnum parseDestinationType(String value) {
        try {
            return TipoDestinoPagoMasivoEnum.valueOf(value == null ? "" : value.trim());
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    "ACCOUNT_INVALID_DESTINATION_TYPE",
                    "El tipo de destino del pago masivo no es válido",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("ACCOUNT_INVALID_AMOUNT", "Monto inválido", HttpStatus.BAD_REQUEST);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal positiveMoney(BigDecimal value) {
        BigDecimal normalized = money(value);
        if (normalized.compareTo(ZERO) <= 0) {
            throw new BusinessException("ACCOUNT_INVALID_AMOUNT", "El monto debe ser mayor a cero", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private BigDecimal nonNullMoney(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeRequired(String value, String code, String message) {
        if (isBlank(value)) throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
        return value.trim();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException notFound(String code, String message) {
        return new BusinessException(code, message, HttpStatus.NOT_FOUND);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible serializar la trazabilidad de pagos masivos", exception);
        }
    }
}
