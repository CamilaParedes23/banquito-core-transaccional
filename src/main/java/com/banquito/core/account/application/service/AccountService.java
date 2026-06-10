package com.banquito.core.account.application.service;

import com.banquito.core.account.api.dto.api.*;
import com.banquito.core.account.domain.enums.*;
import com.banquito.core.account.domain.model.*;
import com.banquito.core.account.domain.repository.*;
import com.banquito.core.account.infrastructure.grpc.client.AdminGrpcClient;
import com.banquito.core.account.infrastructure.grpc.client.AccountingGrpcClient;
import com.banquito.core.account.infrastructure.grpc.client.CustomerGrpcClient;
import com.banquito.core.account.shared.exception.BusinessException;
import com.banquito.core.account.shared.tracing.CorrelationIdHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private final CuentaRepository cuentaRepository;
    private final TransaccionCuentaRepository transaccionRepository;
    private final BloqueoCuentaRepository bloqueoRepository;
    private final HistorialEstadoCuentaRepository historialRepository;
    private final ReservaPagoMasivoRepository reservaRepository;
    private final InstruccionPagoMasivoCoreRepository instruccionRepository;
    private final MovimientoReservaPagoRepository movimientoReservaRepository;
    private final AuditoriaAccountService auditoriaService;
    private final OutboxEventService outboxEventService;
    private final CustomerGrpcClient customerGrpcClient;
    private final AdminGrpcClient adminGrpcClient;
    private final AccountingGrpcClient accountingGrpcClient;
    private final TransactionTemplate transactionTemplate;

    public AccountService(CuentaRepository cuentaRepository,
                          TransaccionCuentaRepository transaccionRepository,
                          BloqueoCuentaRepository bloqueoRepository,
                          HistorialEstadoCuentaRepository historialRepository,
                          ReservaPagoMasivoRepository reservaRepository,
                          InstruccionPagoMasivoCoreRepository instruccionRepository,
                          MovimientoReservaPagoRepository movimientoReservaRepository,
                          AuditoriaAccountService auditoriaService,
                          OutboxEventService outboxEventService,
                          CustomerGrpcClient customerGrpcClient,
                          AdminGrpcClient adminGrpcClient,
                          AccountingGrpcClient accountingGrpcClient,
                          PlatformTransactionManager transactionManager) {
        this.cuentaRepository = cuentaRepository;
        this.transaccionRepository = transaccionRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.historialRepository = historialRepository;
        this.reservaRepository = reservaRepository;
        this.instruccionRepository = instruccionRepository;
        this.movimientoReservaRepository = movimientoReservaRepository;
        this.auditoriaService = auditoriaService;
        this.outboxEventService = outboxEventService;
        this.customerGrpcClient = customerGrpcClient;
        this.adminGrpcClient = adminGrpcClient;
        this.accountingGrpcClient = accountingGrpcClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listByCustomer(String customerUuid, String status, Boolean onlyTransferable, String purpose, Boolean includeBalance) {
        customerGrpcClient.validateActive(customerUuid);
        List<Cuenta> accounts;
        if (purpose != null && !purpose.isBlank()) {
            accounts = cuentaRepository.findByUuidClienteAndPropositoCuentaOrderByFechaAperturaDesc(customerUuid, parseEnum(purpose, PropositoCuentaEnum.class, "ACCOUNT_INVALID_PURPOSE", "Propósito de cuenta inválido"));
        } else if (status != null && !status.isBlank()) {
            accounts = cuentaRepository.findByUuidClienteAndEstadoOrderByFechaAperturaDesc(customerUuid, parseEnum(status, EstadoCuentaEnum.class, "ACCOUNT_INVALID_STATUS", "Estado de cuenta inválido"));
        } else {
            accounts = cuentaRepository.findByUuidClienteOrderByFechaAperturaDesc(customerUuid);
        }
        return accounts.stream()
                .filter(c -> !Boolean.TRUE.equals(onlyTransferable) || c.getEstado() == EstadoCuentaEnum.ACTIVA)
                .map(AccountMapper::toResponse)
                .toList();
    }


    @Transactional(readOnly = true)
    public AccountListResponse listAccounts(String status, String subtypeCode, String branchCode, String accountPurpose, String search, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        EstadoCuentaEnum estado = parseEnum(status, EstadoCuentaEnum.class, "ACCOUNT_INVALID_STATUS", "Estado de cuenta inválido");
        PropositoCuentaEnum proposito = parseEnum(accountPurpose, PropositoCuentaEnum.class, "ACCOUNT_INVALID_PURPOSE", "Propósito de cuenta inválido");
        String normalizedSubtype = blankToNull(subtypeCode);
        String normalizedBranch = blankToNull(branchCode);
        String normalizedSearch = blankToNull(search);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Cuenta> result = cuentaRepository.searchBackofficeAccounts(estado, normalizedSubtype, normalizedBranch, proposito, normalizedSearch, pageable);
        return new AccountListResponse(
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages(),
                result.getContent().stream().map(AccountMapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listByCustomerIdentification(String identification) {
        if (identification == null || identification.isBlank()) {
            throw new BusinessException("ACCOUNT_IDENTIFICATION_REQUIRED", "La identificación del cliente es obligatoria", HttpStatus.BAD_REQUEST);
        }
        return cuentaRepository.findByIdentificacionTitularOrderByFechaAperturaDesc(identification.trim())
                .stream()
                .map(AccountMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAllAccounts(String status) {
        EstadoCuentaEnum estado = parseEnum(status, EstadoCuentaEnum.class, "ACCOUNT_INVALID_STATUS", "Estado de cuenta inválido");
        List<Cuenta> accounts = estado == null
                ? cuentaRepository.findAllByOrderByFechaAperturaDesc()
                : cuentaRepository.findByEstadoOrderByFechaAperturaDesc(estado);
        return accounts.stream().map(AccountMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getByNumber(String accountNumber) { return AccountMapper.toResponse(findAccount(accountNumber)); }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountNumber) { return AccountMapper.toBalance(findAccount(accountNumber)); }

    @Transactional(readOnly = true)
    public List<AccountTransactionResponse> getTransactions(String accountNumber) {
        Cuenta cuenta = findAccount(accountNumber);
        return transaccionRepository.findTop20ByCuentaOrderByTimestampTransaccionDesc(cuenta)
                .stream()
                .map(AccountMapper::toTransaction)
                .toList();
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest r) {
        customerGrpcClient.validateActive(r.customerUuid());
        adminGrpcClient.validateBranchActive(r.branchCode());
        adminGrpcClient.validateAccountSubtypeActive(r.subtypeCode());

        BigDecimal initial = money(r.initialBalance() == null ? ZERO : r.initialBalance());
        Cuenta c = new Cuenta();
        c.setUuidCuenta(UUID.randomUUID().toString());
        c.setNumeroCuenta(generateAccountNumber(r.branchCode()));
        c.setUuidCliente(r.customerUuid());
        c.setIdentificacionTitular(r.identification());
        c.setNombreTitularReferencia(r.holderName());
        c.setCodigoSucursal(r.branchCode());
        c.setCodigoSubtipoCuenta(r.subtypeCode());
        c.setEstado(EstadoCuentaEnum.ACTIVA);
        c.setSaldoContable(initial);
        c.setSaldoDisponible(initial);
        c.setMontoRetenido(ZERO);
        c.setPermiteSobregiro(false);
        c.setLimiteSobregiro(ZERO);
        c.setEsCuentaMatrizPagos(Boolean.TRUE.equals(r.massPaymentMainAccount()));
        c.setEsCuentaFavoritaPagos(Boolean.TRUE.equals(r.favoritePaymentAccount()));
        c.setAliasOperativo(r.operationalAlias());
        c.setPropositoCuenta(r.accountPurpose() == null ? PropositoCuentaEnum.GENERAL : PropositoCuentaEnum.valueOf(r.accountPurpose()));
        c.setFechaApertura(LocalDateTime.now());
        c.setFechaActualizacion(LocalDateTime.now());
        if (Boolean.TRUE.equals(c.getEsCuentaFavoritaPagos())) clearFavorite(c.getUuidCliente());
        Cuenta saved = cuentaRepository.save(c);
        auditoriaService.registrar("CREATE_ACCOUNT", "CUENTA", saved.getUuidCuenta(), ResultadoAuditoriaAccountEnum.OK, null);
        outboxEventService.registrar("ACCOUNT_CREATED", "CUENTA", saved.getUuidCuenta(), "{\"accountNumber\":\"" + saved.getNumeroCuenta() + "\"}");
        return AccountMapper.toResponse(saved);
    }

    @Transactional
    public AccountResponse updateStatus(String accountNumber, UpdateAccountStatusRequest r) {
        Cuenta c = findAccount(accountNumber);
        EstadoCuentaEnum old = c.getEstado();
        EstadoCuentaEnum next = EstadoCuentaEnum.valueOf(r.status());
        c.setEstado(next);
        c.setFechaActualizacion(LocalDateTime.now());
        cuentaRepository.save(c);
        HistorialEstadoCuenta h = new HistorialEstadoCuenta();
        h.setCuenta(c);
        h.setEstadoAnterior(old);
        h.setEstadoNuevo(next);
        h.setMotivoCambio(r.reason());
        h.setUuidUsuarioCore(r.userCoreUuid());
        h.setFechaCambio(LocalDateTime.now());
        historialRepository.save(h);
        return AccountMapper.toResponse(c);
    }

    @Transactional
    public AccountResponse updatePaymentSettings(String accountNumber, UpdatePaymentSettingsRequest r) {
        Cuenta c = findAccount(accountNumber);
        if (r.favoritePaymentAccount() != null) {
            if (r.favoritePaymentAccount()) clearFavorite(c.getUuidCliente());
            c.setEsCuentaFavoritaPagos(r.favoritePaymentAccount());
        }
        if (r.massPaymentMainAccount() != null) c.setEsCuentaMatrizPagos(r.massPaymentMainAccount());
        if (r.accountPurpose() != null) c.setPropositoCuenta(PropositoCuentaEnum.valueOf(r.accountPurpose()));
        if (r.operationalAlias() != null) c.setAliasOperativo(r.operationalAlias());
        c.setFechaActualizacion(LocalDateTime.now());
        return AccountMapper.toResponse(cuentaRepository.save(c));
    }

    @Transactional
    public AccountTransactionResponse deposit(AccountMovementRequest r) {
        String subtype = defaultString(r.subtypeCode(), "DEP_VENTANILLA");
        adminGrpcClient.validateTransactionSubtypeActive(subtype, TipoMovimientoCuentaEnum.CREDITO.name());
        LocalDate date = parseDate(r.accountingDate());
        TransaccionCuenta tx = applyMovement(findAccount(r.accountNumber()), TipoMovimientoCuentaEnum.CREDITO, money(r.amount()), subtype,
                CanalOrigenCuentaEnum.valueOf(defaultString(r.channel(), "VENTANILLA")), r.externalReference(), date, r.correlationId(), null, null);
        registerCashJournal(tx, true);
        return AccountMapper.toTransaction(tx);
    }

    @Transactional
    public AccountTransactionResponse withdraw(AccountMovementRequest r) {
        String subtype = defaultString(r.subtypeCode(), "RET_VENTANILLA");
        adminGrpcClient.validateTransactionSubtypeActive(subtype, TipoMovimientoCuentaEnum.DEBITO.name());
        LocalDate date = parseDate(r.accountingDate());
        TransaccionCuenta tx = applyMovement(findAccount(r.accountNumber()), TipoMovimientoCuentaEnum.DEBITO, money(r.amount()), subtype,
                CanalOrigenCuentaEnum.valueOf(defaultString(r.channel(), "VENTANILLA")), r.externalReference(), date, r.correlationId(), null, null);
        registerCashJournal(tx, false);
        return AccountMapper.toTransaction(tx);
    }

    @Transactional
    public List<AccountTransactionResponse> p2p(P2PTransferRequest r) {
        LocalDate date = parseDate(r.accountingDate());
        String corr = r.correlationId() == null ? UUID.randomUUID().toString() : r.correlationId();
        Cuenta source = findActiveAccount(r.sourceAccountNumber());
        Cuenta target = findActiveAccount(r.targetAccountNumber());
        TransaccionCuenta debit = applyMovement(source, TipoMovimientoCuentaEnum.DEBITO, money(r.amount()), "TRF_P2P_DEB", CanalOrigenCuentaEnum.BANCA_WEB, r.description(), date, corr, null, null);
        TransaccionCuenta credit = applyMovement(target, TipoMovimientoCuentaEnum.CREDITO, money(r.amount()), "TRF_P2P_CRE", CanalOrigenCuentaEnum.BANCA_WEB, r.description(), date, corr, null, null);
        registerP2PJournal(debit, credit);
        return List.of(AccountMapper.toTransaction(debit), AccountMapper.toTransaction(credit));
    }

    @Transactional
    public BlockResponse block(String accountNumber, BlockAccountRequest r) {
        Cuenta c = findActiveAccount(accountNumber);
        BigDecimal amount = money(r.amount());
        ensureAvailable(c, amount);
        c.setMontoRetenido(c.getMontoRetenido().add(amount));
        c.setSaldoDisponible(c.getSaldoDisponible().subtract(amount));
        c.setFechaActualizacion(LocalDateTime.now());
        BloqueoCuenta b = new BloqueoCuenta();
        b.setUuidBloqueo(UUID.randomUUID().toString());
        b.setCuenta(c);
        b.setMontoBloqueado(amount);
        b.setMotivo(r.reason());
        b.setAutoridadOrdenante(r.orderingAuthority());
        b.setEstado(EstadoBloqueoCuentaEnum.ACTIVO);
        b.setUuidUsuarioCore(r.userCoreUuid());
        b.setFechaBloqueo(LocalDateTime.now());
        cuentaRepository.save(c);
        return AccountMapper.toBlock(bloqueoRepository.save(b));
    }

    @Transactional
    public BlockResponse releaseBlock(String accountNumber, String blockUuid) {
        Cuenta c = findAccount(accountNumber);
        BloqueoCuenta b = bloqueoRepository.findByUuidBloqueo(blockUuid).orElseThrow(() -> notFound("ACCOUNT_BLOCK_NOT_FOUND", "Bloqueo no encontrado"));
        if (b.getEstado() != EstadoBloqueoCuentaEnum.ACTIVO) throw new BusinessException("ACCOUNT_BLOCK_NOT_ACTIVE", "El bloqueo no está activo", HttpStatus.CONFLICT);
        b.setEstado(EstadoBloqueoCuentaEnum.LIBERADO);
        b.setFechaLiberacion(LocalDateTime.now());
        c.setMontoRetenido(c.getMontoRetenido().subtract(b.getMontoBloqueado()));
        c.setSaldoDisponible(c.getSaldoDisponible().add(b.getMontoBloqueado()));
        cuentaRepository.save(c);
        return AccountMapper.toBlock(bloqueoRepository.save(b));
    }

    @Transactional
    public AccountTransactionResponse reverseTransaction(String txUuid) {
        TransaccionCuenta original = transaccionRepository.findByUuidTransaccion(txUuid).orElseThrow(() -> notFound("ACCOUNT_TRANSACTION_NOT_FOUND", "Transacción no encontrada"));
        if (original.getEstado() == EstadoTransaccionCuentaEnum.REVERSADA) throw new BusinessException("ACCOUNT_TRANSACTION_ALREADY_REVERSED", "La transacción ya fue reversada", HttpStatus.CONFLICT);
        TipoMovimientoCuentaEnum reverseType = original.getTipoMovimiento() == TipoMovimientoCuentaEnum.DEBITO ? TipoMovimientoCuentaEnum.CREDITO : TipoMovimientoCuentaEnum.DEBITO;
        TransaccionCuenta rev = applyMovement(original.getCuenta(), reverseType, original.getMonto(), "REVERSO", CanalOrigenCuentaEnum.CORE_INTERNO, "Reverso de " + txUuid, original.getFechaContable(), UUID.randomUUID().toString(), null, null);
        rev.setTransaccionReversada(original);
        original.setEstado(EstadoTransaccionCuentaEnum.REVERSADA);
        transaccionRepository.save(original);
        registerGenericClientJournal(rev, "REVERSO", "Reverso de transacción " + txUuid);
        return AccountMapper.toTransaction(rev);
    }

    @Transactional
    public ReservationResponse createReservation(ReservationRequest r) {
        customerGrpcClient.validateMassPaymentsEnabled(r.companyCustomerUuid());
        Cuenta main = findActiveAccount(r.mainAccountNumber());
        if (!Objects.equals(main.getUuidCliente(), r.companyCustomerUuid())) throw new BusinessException("ACCOUNT_RESERVATION_CUSTOMER_MISMATCH", "La cuenta matriz no pertenece a la empresa indicada", HttpStatus.CONFLICT);
        BigDecimal total = money(r.totalAmount()).add(money(r.commissionAmount() == null ? ZERO : r.commissionAmount()));
        ensureAvailable(main, total);
        main.setSaldoDisponible(main.getSaldoDisponible().subtract(total));
        main.setMontoRetenido(main.getMontoRetenido().add(total));
        ReservaPagoMasivo res = new ReservaPagoMasivo();
        res.setUuidReserva(UUID.randomUUID().toString());
        res.setBatchIdExterno(r.batchId());
        res.setUuidCorrelacion(r.correlationId());
        res.setUuidClienteEmpresa(r.companyCustomerUuid());
        res.setCuentaMatriz(main);
        res.setNumeroCuentaMatriz(main.getNumeroCuenta());
        res.setCanalOrigen(CanalOrigenReservaEnum.valueOf(defaultString(r.channel(), "SWITCH_API")));
        res.setMontoTotalLote(money(r.totalAmount()));
        res.setMontoComision(money(r.commissionAmount() == null ? ZERO : r.commissionAmount()));
        res.setMontoReservado(total);
        res.setMontoConsumidoOnus(ZERO);
        res.setMontoConsumidoOffus(ZERO);
        res.setMontoLiberado(ZERO);
        res.setEstado(EstadoReservaPagoMasivoEnum.ACTIVA);
        res.setFechaContable(parseDate(r.accountingDate()));
        res.setFechaCreacion(LocalDateTime.now());
        res.setFechaActualizacion(LocalDateTime.now());
        cuentaRepository.save(main);
        ReservaPagoMasivo saved = reservaRepository.save(res);
        registrarMovimientoReserva(saved, null, null, TipoMovimientoReservaEnum.RESERVA_INICIAL, total, saved.getFechaContable());
        return AccountMapper.toReservation(saved);
    }

    /**
     * Consumo de reserva invocado por Switch.
     *
     * Nota de consistencia distribuida:
     * - Primero confirma la persistencia local de Account en una transacción propia.
     * - Luego registra el asiento contable en Accounting.
     * - Finalmente actualiza las referencias contables locales en una segunda transacción corta.
     *
     * Esto evita el escenario observado donde Accounting queda con asiento registrado,
     * pero Account revierte instrucción/transacción/movimiento por una excepción posterior.
     */
    public ReservationResponse consumeReservation(String reservationUuid, ConsumeReservationRequest r) {
        ConsumptionResult result = transactionTemplate.execute(status -> consumeReservationLocally(reservationUuid, r));
        if (result == null) {
            throw new BusinessException("ACCOUNT_CONSUMPTION_NOT_PROCESSED", "No fue posible procesar el consumo de la reserva", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (result.accountingPayload() != null && result.transactionUuid() != null && result.movementUuid() != null) {
            String asientoContableUuid = accountingGrpcClient.createJournalEntry(result.accountingPayload());
            transactionTemplate.executeWithoutResult(status -> updateAccountingReference(result.transactionUuid(), result.movementUuid(), asientoContableUuid));
        }

        return result.response();
    }

    private ConsumptionResult consumeReservationLocally(String reservationUuid, ConsumeReservationRequest r) {
        ReservaPagoMasivo res = findReservation(reservationUuid);

        Optional<InstruccionPagoMasivoCore> existingInstruction = instruccionRepository.findByPaymentLineUuid(r.paymentLineUuid());
        if (existingInstruction.isPresent()) {
            InstruccionPagoMasivoCore instruction = existingInstruction.get();
            if (!Objects.equals(instruction.getReservaPagoMasivo().getUuidReserva(), reservationUuid)) {
                throw new BusinessException("ACCOUNT_PAYMENT_LINE_ALREADY_USED", "La línea de pago ya fue consumida en otra reserva", HttpStatus.CONFLICT);
            }
            return new ConsumptionResult(AccountMapper.toReservation(instruction.getReservaPagoMasivo()), null, null, null, instruction.getPaymentLineUuid());
        }

        validarReservaConsumible(res);

        BigDecimal amount = money(r.amount());
        BigDecimal consumed = res.getMontoConsumidoOnus().add(res.getMontoConsumidoOffus()).add(res.getMontoLiberado());
        if (consumed.add(amount).compareTo(res.getMontoReservado()) > 0) {
            throw new BusinessException("ACCOUNT_RESERVATION_INSUFFICIENT", "La reserva no tiene saldo suficiente", HttpStatus.CONFLICT);
        }

        TipoDestinoPagoMasivoEnum destinationType = parseEnum(r.destinationType(), TipoDestinoPagoMasivoEnum.class, "ACCOUNT_INVALID_DESTINATION_TYPE", "Tipo de destino de pago inválido");
        Cuenta destination = null;
        if (r.destinationAccountNumber() != null && !r.destinationAccountNumber().isBlank()) {
            destination = findActiveAccount(r.destinationAccountNumber());
        }
        if (destinationType == TipoDestinoPagoMasivoEnum.ON_US && destination == null) {
            throw new BusinessException("ACCOUNT_DESTINATION_REQUIRED", "La cuenta destino On-Us es obligatoria", HttpStatus.BAD_REQUEST);
        }

        InstruccionPagoMasivoCore i = new InstruccionPagoMasivoCore();
        i.setUuidInstruccion(UUID.randomUUID().toString());
        i.setReservaPagoMasivo(res);
        i.setBatchIdExterno(res.getBatchIdExterno());
        i.setPaymentLineUuid(r.paymentLineUuid());
        i.setUuidCorrelacion(defaultString(r.correlationId(), res.getUuidCorrelacion()));
        i.setTipoDestino(destinationType);
        i.setRoutingCodeDestino(r.routingCode());
        i.setCuentaDestino(destination);
        i.setNumeroCuentaDestino(r.destinationAccountNumber());
        i.setCuentaDestinoExterna(r.externalDestinationAccount());
        i.setIdentificacionBeneficiario(r.beneficiaryIdentification());
        i.setNombreBeneficiario(r.beneficiaryName());
        i.setEmailBeneficiario(r.beneficiaryEmail());
        i.setConcepto(r.concept());
        i.setMonto(amount);
        i.setEstado(EstadoInstruccionPagoMasivoEnum.EJECUTADA);
        i.setFechaContable(parseDate(r.accountingDate()));
        i.setFechaCreacion(LocalDateTime.now());
        i.setFechaActualizacion(LocalDateTime.now());
        i = instruccionRepository.saveAndFlush(i);

        TransaccionCuenta tx = null;
        Map<String, Object> accountingPayload = null;
        if (destinationType == TipoDestinoPagoMasivoEnum.ON_US) {
            tx = applyMovement(destination, TipoMovimientoCuentaEnum.CREDITO, amount, "PAGO_ONUS", CanalOrigenCuentaEnum.SWITCH, r.concept(), i.getFechaContable(), i.getUuidCorrelacion(), res, i);
            res.setMontoConsumidoOnus(res.getMontoConsumidoOnus().add(amount));
            accountingPayload = onUsPaymentJournalPayload(tx);
        } else {
            res.setMontoConsumidoOffus(res.getMontoConsumidoOffus().add(amount));
        }

        MovimientoReservaPago movimiento = registrarMovimientoReserva(
                res,
                i,
                tx,
                destinationType == TipoDestinoPagoMasivoEnum.ON_US ? TipoMovimientoReservaEnum.CONSUMO_ONUS : TipoMovimientoReservaEnum.CONSUMO_OFFUS,
                amount,
                i.getFechaContable());

        updateReservationState(res);
        ReservaPagoMasivo savedReservation = reservaRepository.save(res);
        flushLocalPersistence();

        return new ConsumptionResult(
                AccountMapper.toReservation(savedReservation),
                tx == null ? null : tx.getUuidTransaccion(),
                movimiento.getUuidMovimiento(),
                accountingPayload,
                i.getPaymentLineUuid());
    }

    private void updateAccountingReference(String transactionUuid, String movementUuid, String asientoContableUuid) {
        if (transactionUuid == null || transactionUuid.isBlank() || movementUuid == null || movementUuid.isBlank()) {
            log.warn("No se actualiza referencia contable por UUIDs vacíos. transactionUuid={}, movementUuid={}, asientoContableUuid={}", transactionUuid, movementUuid, asientoContableUuid);
            return;
        }
        TransaccionCuenta tx = transaccionRepository.findByUuidTransaccion(transactionUuid)
                .orElseThrow(() -> notFound("ACCOUNT_TRANSACTION_NOT_FOUND", "Transacción no encontrada para actualizar referencia contable"));
        MovimientoReservaPago movimiento = movimientoReservaRepository.findByUuidMovimiento(movementUuid)
                .orElseThrow(() -> notFound("ACCOUNT_RESERVATION_MOVEMENT_NOT_FOUND", "Movimiento de reserva no encontrado para actualizar referencia contable"));
        tx.setAsientoContableUuid(asientoContableUuid);
        movimiento.setAsientoContableUuid(asientoContableUuid);
        transaccionRepository.saveAndFlush(tx);
        movimientoReservaRepository.saveAndFlush(movimiento);
    }

    @Transactional
    public ReservationResponse releaseReservation(String reservationUuid) {
        ReservaPagoMasivo res = findReservation(reservationUuid);
        BigDecimal remaining = res.getMontoReservado().subtract(res.getMontoConsumidoOnus()).subtract(res.getMontoConsumidoOffus()).subtract(res.getMontoLiberado());
        if (remaining.compareTo(ZERO) > 0) {
            Cuenta main = res.getCuentaMatriz();
            main.setMontoRetenido(main.getMontoRetenido().subtract(remaining));
            main.setSaldoDisponible(main.getSaldoDisponible().add(remaining));
            res.setMontoLiberado(res.getMontoLiberado().add(remaining));
            registrarMovimientoReserva(res, null, null, TipoMovimientoReservaEnum.LIBERACION_SOBRANTE, remaining, res.getFechaContable());
            cuentaRepository.save(main);
        }
        res.setEstado(EstadoReservaPagoMasivoEnum.LIBERADA);
        return AccountMapper.toReservation(reservaRepository.save(res));
    }

    @Transactional
    public ReservationResponse reverseReservation(String reservationUuid) {
        ReservaPagoMasivo res = findReservation(reservationUuid);
        res.setEstado(EstadoReservaPagoMasivoEnum.REVERSADA);
        return AccountMapper.toReservation(reservaRepository.save(res));
    }

    @Transactional
    public ReservationResponse closeReservation(String reservationUuid) {
        ReservaPagoMasivo res = findReservation(reservationUuid);
        updateReservationState(res);
        if (res.getEstado() == EstadoReservaPagoMasivoEnum.CONSUMIDA_PARCIAL) res.setEstado(EstadoReservaPagoMasivoEnum.LIBERADA);
        return AccountMapper.toReservation(reservaRepository.save(res));
    }

    @Transactional
    public ReservationResponse chargeServiceFee(String reservationUuid, FeeChargeRequest r) {
        ReservaPagoMasivo res = findReservation(reservationUuid);
        BigDecimal amount = money(r.amount() == null ? res.getMontoComision() : r.amount());
        if (amount.compareTo(ZERO) > 0) {
            TransaccionCuenta tx = applyMovement(res.getCuentaMatriz(), TipoMovimientoCuentaEnum.DEBITO, amount, "COMISION_PM", CanalOrigenCuentaEnum.SWITCH, r.externalReference(), parseDate(r.accountingDate()), r.correlationId() == null ? res.getUuidCorrelacion() : r.correlationId(), res, null);
            registerServiceFeeJournal(tx);
        }
        registrarMovimientoReserva(res, null, null, TipoMovimientoReservaEnum.COMISION, amount, parseDate(r.accountingDate()));
        return AccountMapper.toReservation(res);
    }

    private TransaccionCuenta applyMovement(Cuenta c, TipoMovimientoCuentaEnum type, BigDecimal amount, String subtype, CanalOrigenCuentaEnum channel, String externalRef, LocalDate date, String correlation, ReservaPagoMasivo reserva, InstruccionPagoMasivoCore instruccion) {
        adminGrpcClient.validateTransactionSubtypeActive(subtype, type.name());
        if (type == TipoMovimientoCuentaEnum.DEBITO) {
            c = findActiveAccount(c.getNumeroCuenta());
            ensureAvailable(c, amount);
        } else if (c.getEstado() != EstadoCuentaEnum.ACTIVA) {
            throw new BusinessException("ACCOUNT_NOT_ACTIVE", "La cuenta no está activa", HttpStatus.CONFLICT);
        }
        BigDecimal previous = c.getSaldoContable();
        if (type == TipoMovimientoCuentaEnum.CREDITO) {
            c.setSaldoContable(c.getSaldoContable().add(amount));
            c.setSaldoDisponible(c.getSaldoDisponible().add(amount));
        } else {
            c.setSaldoContable(c.getSaldoContable().subtract(amount));
            c.setSaldoDisponible(c.getSaldoDisponible().subtract(amount));
        }
        c.setFechaActualizacion(LocalDateTime.now());
        TransaccionCuenta t = new TransaccionCuenta();
        t.setUuidTransaccion(UUID.randomUUID().toString());
        t.setUuidCorrelacion(correlation == null ? CorrelationIdHolder.get() : correlation);
        t.setCuenta(c);
        t.setCodigoSubtipoTransaccion(subtype);
        t.setReservaPagoMasivo(reserva);
        t.setInstruccionPagoCore(instruccion);
        t.setFechaContable(date);
        t.setTimestampTransaccion(LocalDateTime.now());
        t.setTipoMovimiento(type);
        t.setMonto(amount);
        t.setSaldoContableAnterior(previous);
        t.setSaldoContableResultante(c.getSaldoContable());
        t.setSaldoDisponibleResultante(c.getSaldoDisponible());
        t.setEstado(EstadoTransaccionCuentaEnum.APLICADA);
        t.setCanalOrigen(channel);
        t.setReferenciaExterna(externalRef);
        t.setNumeroComprobante("CMP-" + System.currentTimeMillis());
        t.setFechaCreacion(LocalDateTime.now());
        cuentaRepository.save(c);
        TransaccionCuenta saved = transaccionRepository.saveAndFlush(t);
        outboxEventService.registrar("ACCOUNT_TRANSACTION_APPLIED", "TRANSACCION_CUENTA", saved.getUuidTransaccion(), "{\"accountNumber\":\"" + c.getNumeroCuenta() + "\"}");
        return saved;
    }

    private void registerCashJournal(TransaccionCuenta tx, boolean deposit) {
        List<Map<String, Object>> lines = deposit
                ? List.of(line(null, "BOVEDA_CENTRAL", "DEBITO", tx.getMonto(), "Ingreso de efectivo", 1), line(null, "CLIENTES_PASIVO", "CREDITO", tx.getMonto(), "Abono a cuenta cliente", 2))
                : List.of(line(null, "CLIENTES_PASIVO", "DEBITO", tx.getMonto(), "Retiro de cuenta cliente", 1), line(null, "BOVEDA_CENTRAL", "CREDITO", tx.getMonto(), "Salida de efectivo", 2));
        accountingGrpcClient.createJournalEntry(journal(tx, deposit ? "DEPOSITO_EFECTIVO" : "RETIRO_EFECTIVO", deposit ? "Depósito en efectivo" : "Retiro en efectivo", lines));
    }

    private void registerP2PJournal(TransaccionCuenta debit, TransaccionCuenta credit) {
        List<Map<String, Object>> lines = List.of(
                line(null, "CLIENTES_PASIVO", "DEBITO", debit.getMonto(), "Débito cuenta origen " + debit.getCuenta().getNumeroCuenta(), 1),
                line(null, "CLIENTES_PASIVO", "CREDITO", credit.getMonto(), "Crédito cuenta destino " + credit.getCuenta().getNumeroCuenta(), 2));
        accountingGrpcClient.createJournalEntry(journal(debit, "TRANSFERENCIA_P2P", "Transferencia interna P2P", lines));
    }

    private String registerOnUsPaymentJournal(TransaccionCuenta tx) {
        return accountingGrpcClient.createJournalEntry(onUsPaymentJournalPayload(tx));
    }

    private Map<String, Object> onUsPaymentJournalPayload(TransaccionCuenta tx) {
        List<Map<String, Object>> lines = List.of(
                line(null, "FONDOS_RESERVADOS_PM", "DEBITO", tx.getMonto(), "Uso de fondos reservados pago masivo", 1),
                line(null, "CLIENTES_PASIVO", "CREDITO", tx.getMonto(), "Acreditación beneficiario On-Us", 2));
        return journal(tx, "PAGO_MASIVO_ONUS", "Acreditación de pago masivo On-Us", lines);
    }

    private void registerServiceFeeJournal(TransaccionCuenta tx) {
        BigDecimal ivaRate = adminGrpcClient.getIvaRate();
        BigDecimal divisor = ONE_HUNDRED.add(ivaRate).divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
        BigDecimal net = tx.getMonto().divide(divisor, 2, RoundingMode.HALF_UP);
        BigDecimal iva = tx.getMonto().subtract(net).setScale(2, RoundingMode.HALF_UP);
        List<Map<String, Object>> lines = List.of(
                line(null, "CLIENTES_PASIVO", "DEBITO", tx.getMonto(), "Cobro comisión pagos masivos", 1),
                line(null, "INGRESOS_SERVICIOS_MASIVOS", "CREDITO", net, "Ingreso neto comisión", 2),
                line(null, "IVA_RETENIDO", "CREDITO", iva, "IVA retenido comisión", 3));
        accountingGrpcClient.createJournalEntry(journal(tx, "COMISION_PAGOS_MASIVOS", "Cobro de comisión de pagos masivos", lines));
    }

    private void registerGenericClientJournal(TransaccionCuenta tx, String operationType, String description) {
        String debit = tx.getTipoMovimiento() == TipoMovimientoCuentaEnum.DEBITO ? "CLIENTES_PASIVO" : "BOVEDA_CENTRAL";
        String credit = tx.getTipoMovimiento() == TipoMovimientoCuentaEnum.DEBITO ? "BOVEDA_CENTRAL" : "CLIENTES_PASIVO";
        List<Map<String, Object>> lines = List.of(
                line(null, debit, "DEBITO", tx.getMonto(), description, 1),
                line(null, credit, "CREDITO", tx.getMonto(), description, 2));
        accountingGrpcClient.createJournalEntry(journal(tx, operationType, description, lines));
    }

    private Map<String, Object> journal(TransaccionCuenta tx, String operationType, String description, List<Map<String, Object>> lines) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("correlationId", tx.getUuidCorrelacion());
        payload.put("transactionUuid", tx.getUuidTransaccion());
        payload.put("originContext", tx.getCanalOrigen() == CanalOrigenCuentaEnum.SWITCH ? "SWITCH" : tx.getCanalOrigen() == CanalOrigenCuentaEnum.VENTANILLA ? "VENTANILLA" : tx.getCanalOrigen() == CanalOrigenCuentaEnum.BANCA_WEB ? "BANCA_WEB" : "ACCOUNT");
        payload.put("operationType", operationType);
        payload.put("description", description);
        payload.put("accountingDate", tx.getFechaContable().toString());
        payload.put("externalReference", tx.getReferenciaExterna());
        payload.put("lines", lines);
        return payload;
    }

    private Map<String, Object> line(String accountingCode, String institutionalAccountCode, String movementType, BigDecimal amount, String reference, int order) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("accountingCode", accountingCode);
        line.put("institutionalAccountCode", institutionalAccountCode);
        line.put("movementType", movementType);
        line.put("amount", amount);
        line.put("reference", reference);
        line.put("lineOrder", order);
        return line;
    }

    private MovimientoReservaPago registrarMovimientoReserva(ReservaPagoMasivo res, InstruccionPagoMasivoCore i, TransaccionCuenta t, TipoMovimientoReservaEnum type, BigDecimal amount, LocalDate date) {
        MovimientoReservaPago m = new MovimientoReservaPago();
        m.setUuidMovimiento(UUID.randomUUID().toString());
        m.setReservaPagoMasivo(res);
        m.setInstruccionPagoCore(i);
        m.setTransaccionCuenta(t);
        m.setPaymentLineUuid(i == null ? null : i.getPaymentLineUuid());
        m.setTipoMovimiento(type);
        m.setMonto(amount);
        m.setEstado(EstadoMovimientoReservaEnum.APLICADO);
        m.setFechaContable(date);
        m.setFechaCreacion(LocalDateTime.now());
        return movimientoReservaRepository.save(m);
    }

    private void updateReservationState(ReservaPagoMasivo r) {
        BigDecimal consumed = r.getMontoConsumidoOnus().add(r.getMontoConsumidoOffus()).add(r.getMontoLiberado());
        if (consumed.compareTo(r.getMontoReservado()) >= 0) r.setEstado(EstadoReservaPagoMasivoEnum.CONSUMIDA_TOTAL);
        else r.setEstado(EstadoReservaPagoMasivoEnum.CONSUMIDA_PARCIAL);
        r.setFechaActualizacion(LocalDateTime.now());
    }

    private void validarReservaConsumible(ReservaPagoMasivo r) {
        if (r.getEstado() != EstadoReservaPagoMasivoEnum.ACTIVA && r.getEstado() != EstadoReservaPagoMasivoEnum.CONSUMIDA_PARCIAL) {
            throw new BusinessException("ACCOUNT_RESERVATION_NOT_CONSUMABLE", "La reserva no se encuentra disponible para consumo", HttpStatus.CONFLICT);
        }
    }

    private void flushLocalPersistence() {
        cuentaRepository.flush();
        instruccionRepository.flush();
        transaccionRepository.flush();
        movimientoReservaRepository.flush();
        reservaRepository.flush();
        outboxEventService.flush();
    }

    private Cuenta findAccount(String number) { return cuentaRepository.findByNumeroCuenta(number).orElseThrow(() -> notFound("ACCOUNT_NOT_FOUND", "Cuenta no encontrada")); }
    private Cuenta findActiveAccount(String number) { Cuenta c = findAccount(number); if (c.getEstado() != EstadoCuentaEnum.ACTIVA) throw new BusinessException("ACCOUNT_NOT_ACTIVE", "La cuenta no está activa", HttpStatus.CONFLICT); return c; }
    private ReservaPagoMasivo findReservation(String uuid) { return reservaRepository.findByUuidReserva(uuid).orElseThrow(() -> notFound("ACCOUNT_RESERVATION_NOT_FOUND", "Reserva no encontrada")); }
    private BusinessException notFound(String code, String msg) { return new BusinessException(code, msg, HttpStatus.NOT_FOUND); }
    private void ensureAvailable(Cuenta c, BigDecimal amount) { if (c.getSaldoDisponible().compareTo(amount) < 0) throw new BusinessException("ACCOUNT_INSUFFICIENT_FUNDS", "Saldo disponible insuficiente", HttpStatus.CONFLICT); }
    private BigDecimal money(BigDecimal v) { if (v == null || v.compareTo(ZERO) < 0) throw new BusinessException("ACCOUNT_INVALID_AMOUNT", "Monto inválido", HttpStatus.BAD_REQUEST); return v.setScale(2, RoundingMode.HALF_UP); }
    private LocalDate parseDate(String v) { return v == null || v.isBlank() ? accountingGrpcClient.getCurrentAccountingDate() : LocalDate.parse(v); }
    private void clearFavorite(String customerUuid) { cuentaRepository.findByUuidClienteAndEsCuentaFavoritaPagos(customerUuid, true).ifPresent(c -> { c.setEsCuentaFavoritaPagos(false); cuentaRepository.save(c); }); }
    private String generateAccountNumber(String branchCode) { return branchCode + String.format("%010d", System.currentTimeMillis() % 10000000000L); }
    private String defaultString(String value, String defaultValue) { return value == null || value.isBlank() ? defaultValue : value; }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> enumType, String code, String message) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
        }
    }
    private record ConsumptionResult(ReservationResponse response, String transactionUuid, String movementUuid, Map<String, Object> accountingPayload, String paymentLineUuid) {}

}
