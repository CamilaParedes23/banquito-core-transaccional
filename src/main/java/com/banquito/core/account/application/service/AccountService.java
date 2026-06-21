package com.banquito.core.account.application.service;

import com.banquito.core.account.api.dto.api.*;
import com.banquito.core.account.domain.enums.*;
import com.banquito.core.account.domain.model.*;
import com.banquito.core.account.domain.repository.*;
import com.banquito.core.account.infrastructure.grpc.client.AdminGrpcClient;
import com.banquito.core.account.infrastructure.grpc.client.AccountingGrpcClient;
import com.banquito.core.account.infrastructure.grpc.client.CustomerGrpcClient;
import com.banquito.core.admin.infrastructure.grpc.generated.AccountSubtypeResponse;
import com.banquito.core.customer.infrastructure.grpc.CustomerBasicGrpcResponse;
import com.banquito.core.account.shared.exception.BusinessException;
import com.banquito.core.account.shared.tracing.CorrelationIdHolder;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
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
    private static final String OPENING_DEPOSIT_SUBTYPE = "DEP_APERTURA_CUENTA";
    private static final String OPENING_DEPOSIT_DESCRIPTION = "Depósito inicial de apertura";

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
    private final AccountBlockExpirationService blockExpirationService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

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
                          AccountBlockExpirationService blockExpirationService,
                          PlatformTransactionManager transactionManager,
                          ObjectMapper objectMapper) {
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
        this.blockExpirationService = blockExpirationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listByCustomer(String customerUuid, String status, Boolean onlyTransferable, String purpose, Boolean includeBalance) {
        String normalizedCustomerUuid = normalizeRequired(customerUuid, "CUSTOMER_UUID_REQUIRED", "El UUID del cliente es obligatorio");
        // Consulta de lectura: Account es la fuente de cuentas y ya mantiene el UUID del cliente.
        // No se bloquea el listado con una validación remota a Customer, para mantener simetría
        // con /by-customer-identification y evitar acoplamiento fuerte en consultas.

        List<Cuenta> accounts;
        if (purpose != null && !purpose.isBlank()) {
            accounts = cuentaRepository.findByUuidClienteAndPropositoCuentaOrderByFechaAperturaDesc(normalizedCustomerUuid, parseEnum(purpose, PropositoCuentaEnum.class, "ACCOUNT_INVALID_PURPOSE", "Propósito de cuenta inválido"));
        } else if (status != null && !status.isBlank()) {
            accounts = cuentaRepository.findByUuidClienteAndEstadoOrderByFechaAperturaDesc(normalizedCustomerUuid, parseEnum(status, EstadoCuentaEnum.class, "ACCOUNT_INVALID_STATUS", "Estado de cuenta inválido"));
        } else {
            accounts = cuentaRepository.findByUuidClienteOrderByFechaAperturaDesc(normalizedCustomerUuid);
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

    @Transactional
    public BalanceResponse getBalance(String accountNumber) {
        blockExpirationService.expireForAccount(accountNumber);
        return AccountMapper.toBalance(findAccount(accountNumber));
    }

    @Transactional(readOnly = true)
    public List<AccountTransactionResponse> getTransactions(String accountNumber) {
        Cuenta cuenta = findAccount(accountNumber);
        return toTransactionResponses(
                transaccionRepository.findTop20ByCuentaOrderByTimestampTransaccionDesc(cuenta));
    }

    @Transactional(readOnly = true)
    public AccountTransactionResponse getTransaction(String transactionUuid) {
        TransaccionCuenta transaction = transaccionRepository.findByUuidTransaccion(transactionUuid)
                .orElseThrow(() -> notFound("ACCOUNT_TRANSACTION_NOT_FOUND", "Transacción no encontrada"));
        return toTransactionResponse(transaction);
    }

    @Transactional
    public List<BlockResponse> listBlocks(String accountNumber, String status) {
        blockExpirationService.expireForAccount(accountNumber);
        Cuenta account = findAccount(accountNumber);
        EstadoBloqueoCuentaEnum blockStatus = parseEnum(
                status,
                EstadoBloqueoCuentaEnum.class,
                "ACCOUNT_BLOCK_STATUS_INVALID",
                "Estado de bloqueo inválido");
        List<BloqueoCuenta> blocks = blockStatus == null
                ? bloqueoRepository.findByCuentaOrderByFechaBloqueoDesc(account)
                : bloqueoRepository.findByCuentaAndEstadoOrderByFechaBloqueoDesc(account, blockStatus);
        return blocks.stream().map(AccountMapper::toBlock).toList();
    }

    @Transactional(readOnly = true)
    public List<AccountStatusHistoryResponse> listStatusHistory(String accountNumber) {
        Cuenta account = findAccount(accountNumber);
        return historialRepository.findByCuentaOrderByFechaCambioDesc(account)
                .stream()
                .map(AccountMapper::toStatusHistory)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountTransactionResponse getTransactionByReceipt(String receiptNumber) {
        String normalizedReceipt = normalizeRequired(
                receiptNumber,
                "ACCOUNT_RECEIPT_NUMBER_REQUIRED",
                "El número de comprobante es obligatorio");
        TransaccionCuenta transaction = transaccionRepository.findByNumeroComprobante(normalizedReceipt)
                .orElseThrow(() -> notFound("ACCOUNT_TRANSACTION_NOT_FOUND", "Transacción no encontrada"));
        return toTransactionResponse(transaction);
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest r) {
        CustomerBasicGrpcResponse customer = customerGrpcClient.getByUuid(r.customerUuid());
        if (!"ACTIVO".equalsIgnoreCase(customer.getStatus())) {
            throw new BusinessException("CUSTOMER_NOT_ACTIVE", "El cliente no está activo", HttpStatus.CONFLICT);
        }
        if (!customer.getIdentification().equals(r.identification())) {
            throw new BusinessException("ACCOUNT_CUSTOMER_IDENTIFICATION_MISMATCH",
                    "La identificación no corresponde al cliente indicado", HttpStatus.CONFLICT);
        }

        adminGrpcClient.validateBranchActive(r.branchCode());
        AccountSubtypeResponse product = adminGrpcClient.getActiveAccountSubtype(r.subtypeCode());
        PropositoCuentaEnum purpose = parseEnum(
                defaultString(r.accountPurpose(), PropositoCuentaEnum.GENERAL.name()),
                PropositoCuentaEnum.class,
                "ACCOUNT_INVALID_PURPOSE",
                "Propósito de cuenta inválido");
        validateAccountProductEligibility(product, customer, purpose, r);

        BigDecimal initial = money(r.initialBalance() == null ? ZERO : r.initialBalance());
        BigDecimal minimumOpeningBalance = product.getMinimumOpeningBalance().isBlank()
                ? ZERO
                : new BigDecimal(product.getMinimumOpeningBalance()).setScale(2, RoundingMode.HALF_UP);
        if (initial.compareTo(minimumOpeningBalance) < 0) {
            throw new BusinessException("ACCOUNT_MINIMUM_OPENING_BALANCE_NOT_MET",
                    "El saldo inicial es menor al mínimo requerido para el producto", HttpStatus.CONFLICT);
        }

        Cuenta c = new Cuenta();
        c.setUuidCuenta(UUID.randomUUID().toString());
        c.setNumeroCuenta(generateAccountNumber(r.branchCode()));
        c.setUuidCliente(customer.getCustomerUuid());
        c.setIdentificacionTitular(customer.getIdentification());
        c.setNombreTitularReferencia(customer.getDisplayName());
        c.setCodigoSucursal(r.branchCode());
        c.setCodigoSubtipoCuenta(r.subtypeCode());
        c.setEstado(EstadoCuentaEnum.ACTIVA);
        // La cuenta nace siempre en cero. Si existe fondeo inicial, se aplica como
        // una transacción financiera trazable dentro de esta misma transacción local.
        c.setSaldoContable(ZERO.setScale(2, RoundingMode.HALF_UP));
        c.setSaldoDisponible(ZERO.setScale(2, RoundingMode.HALF_UP));
        c.setMontoRetenido(ZERO.setScale(2, RoundingMode.HALF_UP));
        c.setPermiteSobregiro(false);
        c.setLimiteSobregiro(ZERO.setScale(2, RoundingMode.HALF_UP));
        c.setEsCuentaMatrizPagos(Boolean.TRUE.equals(r.massPaymentMainAccount()));
        c.setEsCuentaFavoritaPagos(Boolean.TRUE.equals(r.favoritePaymentAccount()));
        c.setAliasOperativo(blankToNull(r.operationalAlias()));
        c.setPropositoCuenta(purpose);
        c.setFechaApertura(LocalDateTime.now());
        c.setFechaActualizacion(LocalDateTime.now());
        if (Boolean.TRUE.equals(c.getEsCuentaFavoritaPagos())) clearFavorite(c.getUuidCliente());
        Cuenta saved = cuentaRepository.saveAndFlush(c);

        if (initial.compareTo(ZERO) > 0) {
            LocalDate accountingDate = accountingGrpcClient.resolveOperationAccountingDate();
            String correlationId = CorrelationIdHolder.get();
            TransaccionCuenta openingDeposit = applyMovement(
                    saved,
                    TipoMovimientoCuentaEnum.CREDITO,
                    initial,
                    OPENING_DEPOSIT_SUBTYPE,
                    CanalOrigenCuentaEnum.VENTANILLA,
                    OPENING_DEPOSIT_DESCRIPTION,
                    accountingDate,
                    correlationId,
                    null,
                    null);
            markAccountingSucceeded(openingDeposit, registerOpeningDepositJournal(openingDeposit));
            transaccionRepository.saveAndFlush(openingDeposit);
            registerOpeningDepositCompletedEvent(openingDeposit, customer.getEmail());
        }

        auditoriaService.registrar(
                "CREATE_ACCOUNT",
                "CUENTA",
                saved.getUuidCuenta(),
                ResultadoAuditoriaAccountEnum.OK,
                toJson(Map.of(
                        "accountNumber", saved.getNumeroCuenta(),
                        "initialBalance", initial)));
        outboxEventService.registrar(
                "ACCOUNT_CREATED",
                "CUENTA",
                saved.getUuidCuenta(),
                toJson(Map.of(
                        "accountNumber", saved.getNumeroCuenta(),
                        "initialBalance", initial)));
        return AccountMapper.toResponse(saved);
    }

    @Transactional
    public AccountResponse updateStatus(String accountNumber, UpdateAccountStatusRequest request, String actorUuid) {
        Cuenta account = findAccount(accountNumber);
        EstadoCuentaEnum previousStatus = account.getEstado();
        EstadoCuentaEnum newStatus = parseEnum(
                request.status(),
                EstadoCuentaEnum.class,
                "ACCOUNT_INVALID_STATUS",
                "Estado de cuenta inválido");
        if (newStatus == EstadoCuentaEnum.CERRADA) {
            throw new BusinessException(
                    "ACCOUNT_CLOSURE_REQUIRES_DEDICATED_PROCESS",
                    "El cierre definitivo requiere un proceso especializado",
                    HttpStatus.CONFLICT);
        }
        if (previousStatus == newStatus) {
            throw new BusinessException(
                    "ACCOUNT_STATUS_UNCHANGED",
                    "La cuenta ya se encuentra en el estado solicitado",
                    HttpStatus.CONFLICT);
        }
        validateStatusTransition(previousStatus, newStatus);

        account.setEstado(newStatus);
        account.setFechaActualizacion(LocalDateTime.now());
        cuentaRepository.save(account);

        HistorialEstadoCuenta history = new HistorialEstadoCuenta();
        history.setCuenta(account);
        history.setEstadoAnterior(previousStatus);
        history.setEstadoNuevo(newStatus);
        history.setMotivoCambio(request.reason().trim());
        history.setUuidUsuarioCore(normalizeActor(actorUuid));
        history.setFechaCambio(LocalDateTime.now());
        historialRepository.save(history);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("accountNumber", account.getNumeroCuenta());
        detail.put("previousStatus", previousStatus.name());
        detail.put("newStatus", newStatus.name());
        detail.put("reason", request.reason().trim());
        detail.put("actorUuid", normalizeActor(actorUuid));
        auditoriaService.registrar(
                "UPDATE_ACCOUNT_STATUS",
                "CUENTA",
                account.getUuidCuenta(),
                ResultadoAuditoriaAccountEnum.OK,
                toJson(detail));
        outboxEventService.registrar(
                "ACCOUNT_STATUS_CHANGED",
                "CUENTA",
                account.getUuidCuenta(),
                CorrelationIdHolder.get(),
                toJson(detail));
        return AccountMapper.toResponse(account);
    }

    @Transactional
    public AccountResponse updatePaymentSettings(String accountNumber, UpdatePaymentSettingsRequest request) {
        Cuenta account = findAccount(accountNumber);
        CustomerBasicGrpcResponse customer = customerGrpcClient.getByUuid(account.getUuidCliente());
        AccountSubtypeResponse product = adminGrpcClient.getActiveAccountSubtype(account.getCodigoSubtipoCuenta());
        PropositoCuentaEnum purpose = request.accountPurpose() == null
                ? account.getPropositoCuenta()
                : parseEnum(request.accountPurpose(), PropositoCuentaEnum.class,
                        "ACCOUNT_INVALID_PURPOSE", "Propósito de cuenta inválido");
        boolean favoritePaymentAccount = request.favoritePaymentAccount() == null
                ? Boolean.TRUE.equals(account.getEsCuentaFavoritaPagos())
                : request.favoritePaymentAccount();
        boolean massPaymentMainAccount = request.massPaymentMainAccount() == null
                ? Boolean.TRUE.equals(account.getEsCuentaMatrizPagos())
                : request.massPaymentMainAccount();
        boolean overdraftAllowed = request.overdraftAllowed() == null
                ? Boolean.TRUE.equals(account.getPermiteSobregiro())
                : request.overdraftAllowed();
        BigDecimal overdraftLimit = Boolean.FALSE.equals(request.overdraftAllowed())
                ? ZERO
                : request.overdraftLimit() == null
                ? nonNegative(account.getLimiteSobregiro())
                : money(request.overdraftLimit());

        validatePaymentSettingsEligibility(
                product,
                customer,
                purpose,
                favoritePaymentAccount,
                massPaymentMainAccount);
        validateMassPaymentOverdraftSettings(
                account,
                product,
                customer,
                massPaymentMainAccount,
                overdraftAllowed,
                overdraftLimit);

        if (request.favoritePaymentAccount() != null) {
            if (request.favoritePaymentAccount()) clearFavorite(account.getUuidCliente());
            account.setEsCuentaFavoritaPagos(request.favoritePaymentAccount());
        }
        if (request.massPaymentMainAccount() != null) {
            account.setEsCuentaMatrizPagos(request.massPaymentMainAccount());
        }
        if (request.accountPurpose() != null) account.setPropositoCuenta(purpose);
        if (request.operationalAlias() != null) account.setAliasOperativo(blankToNull(request.operationalAlias()));
        if (request.overdraftAllowed() != null || request.overdraftLimit() != null) {
            account.setPermiteSobregiro(overdraftAllowed);
            account.setLimiteSobregiro(overdraftAllowed ? overdraftLimit : ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        account.setFechaActualizacion(LocalDateTime.now());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("accountNumber", account.getNumeroCuenta());
        detail.put("favoritePaymentAccount", account.getEsCuentaFavoritaPagos());
        detail.put("massPaymentMainAccount", account.getEsCuentaMatrizPagos());
        detail.put("accountPurpose", account.getPropositoCuenta().name());
        detail.put("overdraftAllowed", account.getPermiteSobregiro());
        detail.put("overdraftLimit", account.getLimiteSobregiro());
        auditoriaService.registrar(
                "UPDATE_ACCOUNT_PAYMENT_SETTINGS",
                "CUENTA",
                account.getUuidCuenta(),
                ResultadoAuditoriaAccountEnum.OK,
                toJson(detail));
        return AccountMapper.toResponse(cuentaRepository.save(account));
    }

    @Transactional
    public AccountTransactionResponse deposit(AccountMovementRequest r) {
        String subtype = defaultString(r.subtypeCode(), "DEP_VENTANILLA");
        adminGrpcClient.validateTransactionSubtypeActive(subtype, TipoMovimientoCuentaEnum.CREDITO.name());
        LocalDate date = parseDate(r.accountingDate());
        Cuenta account = findAccount(r.accountNumber());
        CustomerBasicGrpcResponse customer = customerGrpcClient.getByUuid(account.getUuidCliente());
        TransaccionCuenta tx = applyMovement(account, TipoMovimientoCuentaEnum.CREDITO, money(r.amount()), subtype,
                tellerChannel(r.channel()), r.externalReference(), date, r.correlationId(), null, null);
        markAccountingSucceeded(tx, registerCashJournal(tx, true));
        transaccionRepository.saveAndFlush(tx);
        registerTellerCompletedEvent(tx, "TELLER_DEPOSIT_COMPLETED", "DEPOSITO", customer.getEmail());
        return toTransactionResponse(tx);
    }

    @Transactional
    public AccountTransactionResponse withdraw(AccountMovementRequest r) {
        String subtype = defaultString(r.subtypeCode(), "RET_VENTANILLA");
        adminGrpcClient.validateTransactionSubtypeActive(subtype, TipoMovimientoCuentaEnum.DEBITO.name());
        LocalDate date = parseDate(r.accountingDate());
        blockExpirationService.expireForAccount(r.accountNumber());
        Cuenta account = findAccount(r.accountNumber());
        CustomerBasicGrpcResponse customer = customerGrpcClient.getByUuid(account.getUuidCliente());
        TransaccionCuenta tx = applyMovement(account, TipoMovimientoCuentaEnum.DEBITO, money(r.amount()), subtype,
                tellerChannel(r.channel()), r.externalReference(), date, r.correlationId(), null, null);
        markAccountingSucceeded(tx, registerCashJournal(tx, false));
        transaccionRepository.saveAndFlush(tx);
        registerTellerCompletedEvent(tx, "TELLER_WITHDRAWAL_COMPLETED", "RETIRO", customer.getEmail());
        return toTransactionResponse(tx);
    }

    @Transactional(readOnly = true)
    public P2PBeneficiaryValidationResponse validateP2PBeneficiary(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new BusinessException(
                    "ACCOUNT_NUMBER_REQUIRED",
                    "El número de cuenta destino es obligatorio",
                    HttpStatus.BAD_REQUEST
            );
        }
        return cuentaRepository.findByNumeroCuenta(accountNumber.trim())
                .map(account -> new P2PBeneficiaryValidationResponse(
                        true,
                        account.getEstado().name(),
                        account.getNombreTitularReferencia(),
                        maskAccountNumber(account.getNumeroCuenta()),
                        "Banco BanQuito"
                ))
                .orElseGet(() -> new P2PBeneficiaryValidationResponse(
                        false,
                        "NO_ENCONTRADA",
                        null,
                        maskAccountNumber(accountNumber.trim()),
                        "Banco BanQuito"
                ));
    }

    @Transactional
    public List<AccountTransactionResponse> p2p(P2PTransferRequest r) {
        LocalDate date = parseDate(r.accountingDate());
        String corr = r.correlationId() == null || r.correlationId().isBlank()
                ? UUID.randomUUID().toString()
                : r.correlationId().trim();
        blockExpirationService.expireForAccount(r.sourceAccountNumber());
        Cuenta source = findActiveAccount(r.sourceAccountNumber());
        Cuenta target = findActiveAccount(r.targetAccountNumber());
        CustomerBasicGrpcResponse sourceCustomer = customerGrpcClient.getByUuid(source.getUuidCliente());
        CustomerBasicGrpcResponse targetCustomer = customerGrpcClient.getByUuid(target.getUuidCliente());
        String description = blankToNull(r.description());
        TransaccionCuenta debit = applyMovement(source, TipoMovimientoCuentaEnum.DEBITO, money(r.amount()), "TRF_P2P_DEB", CanalOrigenCuentaEnum.BANCA_WEB, description, date, corr, null, null);
        TransaccionCuenta credit = applyMovement(target, TipoMovimientoCuentaEnum.CREDITO, money(r.amount()), "TRF_P2P_CRE", CanalOrigenCuentaEnum.BANCA_WEB, description, date, corr, null, null);
        String asientoContableUuid = registerP2PJournal(debit, credit);
        markAccountingSucceeded(debit, asientoContableUuid);
        markAccountingSucceeded(credit, asientoContableUuid);
        transaccionRepository.saveAllAndFlush(List.of(debit, credit));

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("correlationId", corr);
        eventPayload.put("sourceCustomerUuid", source.getUuidCliente());
        eventPayload.put("targetCustomerUuid", target.getUuidCliente());
        eventPayload.put("sourceAccountNumber", source.getNumeroCuenta());
        eventPayload.put("targetAccountNumber", target.getNumeroCuenta());
        eventPayload.put("sourceHolderName", source.getNombreTitularReferencia());
        eventPayload.put("targetHolderName", target.getNombreTitularReferencia());
        eventPayload.put("sourceEmail", sourceCustomer.getEmail());
        eventPayload.put("targetEmail", targetCustomer.getEmail());
        eventPayload.put("amount", money(r.amount()));
        eventPayload.put("description", description);
        eventPayload.put("accountingDate", date.toString());
        eventPayload.put("debitTransactionUuid", debit.getUuidTransaccion());
        eventPayload.put("creditTransactionUuid", credit.getUuidTransaccion());
        eventPayload.put("receiptNumber", debit.getNumeroComprobante());
        outboxEventService.registrar(
                "P2P_TRANSFER_COMPLETED",
                "TRANSFERENCIA_P2P",
                corr,
                corr,
                toJson(eventPayload)
        );

        return toTransactionResponses(List.of(debit, credit));
    }

    @Transactional
    public BlockResponse block(String accountNumber, BlockAccountRequest request, String actorUuid) {
        blockExpirationService.expireForAccount(accountNumber);
        Cuenta account = findActiveAccount(accountNumber);
        BigDecimal amount = positiveMoney(request.amount());
        ensureAvailable(account, amount);
        validateBlockExpiration(request.expiresAt());

        account.setMontoRetenido(account.getMontoRetenido().add(amount));
        recalculateAvailableBalance(account);
        account.setFechaActualizacion(LocalDateTime.now());

        BloqueoCuenta block = new BloqueoCuenta();
        block.setUuidBloqueo(UUID.randomUUID().toString());
        block.setCuenta(account);
        block.setMontoBloqueado(amount);
        block.setMotivo(request.reason().trim());
        block.setAutoridadOrdenante(blankToNull(request.orderingAuthority()));
        block.setEstado(EstadoBloqueoCuentaEnum.ACTIVO);
        block.setUuidUsuarioCore(normalizeActor(actorUuid));
        block.setFechaBloqueo(LocalDateTime.now());
        block.setFechaExpiracion(request.expiresAt());
        cuentaRepository.save(account);
        BloqueoCuenta savedBlock = bloqueoRepository.save(block);

        Map<String, Object> detail = blockEventPayload(savedBlock, "CREATED", actorUuid);
        auditoriaService.registrar(
                "CREATE_ACCOUNT_BLOCK",
                "BLOQUEO_CUENTA",
                savedBlock.getUuidBloqueo(),
                ResultadoAuditoriaAccountEnum.OK,
                toJson(detail));
        outboxEventService.registrar(
                "ACCOUNT_BLOCK_CREATED",
                "BLOQUEO_CUENTA",
                savedBlock.getUuidBloqueo(),
                CorrelationIdHolder.get(),
                toJson(detail));
        return AccountMapper.toBlock(savedBlock);
    }

    @Transactional
    public BlockResponse releaseBlock(String accountNumber, String blockUuid, String actorUuid) {
        blockExpirationService.expireForAccount(accountNumber);
        Cuenta account = findAccount(accountNumber);
        BloqueoCuenta block = bloqueoRepository.findByUuidBloqueoAndCuenta(blockUuid, account)
                .orElseThrow(() -> notFound("ACCOUNT_BLOCK_NOT_FOUND", "Bloqueo no encontrado para la cuenta indicada"));
        if (block.getEstado() != EstadoBloqueoCuentaEnum.ACTIVO) {
            throw new BusinessException(
                    "ACCOUNT_BLOCK_NOT_ACTIVE",
                    "El bloqueo no se encuentra activo",
                    HttpStatus.CONFLICT);
        }

        block.setEstado(EstadoBloqueoCuentaEnum.LIBERADO);
        block.setFechaLiberacion(LocalDateTime.now());
        account.setMontoRetenido(nonNegative(account.getMontoRetenido().subtract(block.getMontoBloqueado())));
        recalculateAvailableBalance(account);
        account.setFechaActualizacion(LocalDateTime.now());
        cuentaRepository.save(account);
        BloqueoCuenta savedBlock = bloqueoRepository.save(block);

        Map<String, Object> detail = blockEventPayload(savedBlock, "RELEASED", actorUuid);
        auditoriaService.registrar(
                "RELEASE_ACCOUNT_BLOCK",
                "BLOQUEO_CUENTA",
                savedBlock.getUuidBloqueo(),
                ResultadoAuditoriaAccountEnum.OK,
                toJson(detail));
        outboxEventService.registrar(
                "ACCOUNT_BLOCK_RELEASED",
                "BLOQUEO_CUENTA",
                savedBlock.getUuidBloqueo(),
                CorrelationIdHolder.get(),
                toJson(detail));
        return AccountMapper.toBlock(savedBlock);
    }

    @Transactional
    public AccountTransactionResponse reverseTransaction(String txUuid, ReverseTransactionRequest request) {
        TransaccionCuenta original = transaccionRepository.findByUuidTransaccionForUpdate(txUuid)
                .orElseThrow(() -> notFound("ACCOUNT_TRANSACTION_NOT_FOUND", "Transacción no encontrada"));
        if (original.getTransaccionReversada() != null) {
            throw new BusinessException("ACCOUNT_REVERSAL_CANNOT_BE_REVERSED",
                    "Una transacción compensatoria no puede ser reversada", HttpStatus.CONFLICT);
        }
        if (original.getEstado() == EstadoTransaccionCuentaEnum.REVERSADA
                || transaccionRepository.existsByTransaccionReversada(original)) {
            throw new BusinessException("ACCOUNT_TRANSACTION_ALREADY_REVERSED",
                    "La transacción ya fue reversada", HttpStatus.CONFLICT);
        }
        if (original.getEstado() != EstadoTransaccionCuentaEnum.APLICADA) {
            throw new BusinessException("ACCOUNT_TRANSACTION_NOT_REVERSIBLE",
                    "La transacción no se encuentra en un estado reversible", HttpStatus.CONFLICT);
        }
        if (original.getCanalOrigen() != CanalOrigenCuentaEnum.VENTANILLA) {
            throw new BusinessException("ACCOUNT_TRANSACTION_REVERSAL_UNSUPPORTED",
                    "En esta fase solo se permiten reversos de depósitos y retiros de ventanilla", HttpStatus.CONFLICT);
        }

        TipoMovimientoCuentaEnum reverseType = original.getTipoMovimiento() == TipoMovimientoCuentaEnum.DEBITO
                ? TipoMovimientoCuentaEnum.CREDITO
                : TipoMovimientoCuentaEnum.DEBITO;
        String reverseSubtype = reverseType == TipoMovimientoCuentaEnum.DEBITO
                ? "REVERSO_DEBITO"
                : "REVERSO_CREDITO";
        String correlationId = request.correlationId() == null || request.correlationId().isBlank()
                ? CorrelationIdHolder.get()
                : request.correlationId().trim();

        TransaccionCuenta reversal = applyMovement(
                original.getCuenta(),
                reverseType,
                original.getMonto(),
                reverseSubtype,
                CanalOrigenCuentaEnum.CORE_INTERNO,
                request.reason(),
                original.getFechaContable(),
                correlationId,
                null,
                null);
        reversal.setTransaccionReversada(original);
        reversal.setMotivoReverso(request.reason());
        reversal.setUuidUsuarioReverso(request.userCoreUuid());

        String journalEntryUuid = registerGenericClientJournal(
                reversal,
                reverseSubtype,
                "Reverso de transacción " + txUuid + ": " + request.reason());
        markAccountingSucceeded(reversal, journalEntryUuid);
        original.setEstado(EstadoTransaccionCuentaEnum.REVERSADA);
        transaccionRepository.saveAllAndFlush(List.of(original, reversal));

        auditoriaService.registrar("REVERSE_ACCOUNT_TRANSACTION", "TRANSACCION_CUENTA", txUuid,
                ResultadoAuditoriaAccountEnum.OK,
                "{\"reversalTransactionUuid\":\"" + reversal.getUuidTransaccion()
                        + "\",\"userCoreUuid\":\"" + request.userCoreUuid() + "\"}");
        outboxEventService.registrar("ACCOUNT_TRANSACTION_REVERSED", "TRANSACCION_CUENTA", txUuid,
                "{\"originalTransactionUuid\":\"" + txUuid
                        + "\",\"reversalTransactionUuid\":\"" + reversal.getUuidTransaccion() + "\"}");
        return toTransactionResponse(reversal);
    }

    @Transactional
    public ReservationResponse createReservation(ReservationRequest r) {
        customerGrpcClient.validateMassPaymentsEnabled(r.companyCustomerUuid());
        blockExpirationService.expireForAccount(r.mainAccountNumber());
        Cuenta main = findActiveAccount(r.mainAccountNumber());
        if (!Boolean.TRUE.equals(main.getEsCuentaMatrizPagos())) {
            throw new BusinessException(
                    "ACCOUNT_MASS_PAYMENTS_ACCOUNT_NOT_ENABLED",
                    "La cuenta no está habilitada para pagos masivos",
                    HttpStatus.CONFLICT);
        }
        if (!Objects.equals(main.getUuidCliente(), r.companyCustomerUuid())) throw new BusinessException("ACCOUNT_RESERVATION_CUSTOMER_MISMATCH", "La cuenta matriz no pertenece a la empresa indicada", HttpStatus.CONFLICT);
        BigDecimal totalAmount = positiveMoney(r.totalAmount());
        BigDecimal commissionAmount = money(r.commissionAmount() == null ? ZERO : r.commissionAmount());
        BigDecimal total = totalAmount.add(commissionAmount);
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
        res.setMontoTotalLote(totalAmount);
        res.setMontoComision(commissionAmount);
        res.setMontoComisionCobrado(ZERO);
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
            try {
                String asientoContableUuid = accountingGrpcClient.createJournalEntry(result.accountingPayload());
                transactionTemplate.executeWithoutResult(status -> updateAccountingReference(result.transactionUuid(), result.movementUuid(), asientoContableUuid));
            } catch (BusinessException exception) {
                markAccountingRequiresReconciliation(result.transactionUuid(), exception.getCode(), exception.getMessage());
                throw exception;
            } catch (RuntimeException exception) {
                markAccountingRequiresReconciliation(result.transactionUuid(), "ACCOUNTING_RECONCILIATION_REQUIRED", exception.getMessage());
                throw exception;
            }
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

        BigDecimal amount = positiveMoney(r.amount());
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
            tx.setEstadoContabilizacion(EstadoContabilizacionCuentaEnum.CONTABILIZADA);
            tx.setFechaContabilizacion(LocalDateTime.now());
            tx.setCodigoErrorContable(null);
            tx.setMensajeErrorContable(null);
        movimiento.setAsientoContableUuid(asientoContableUuid);
        transaccionRepository.saveAndFlush(tx);
        movimientoReservaRepository.saveAndFlush(movimiento);

        InstruccionPagoMasivoCore instruction = tx.getInstruccionPagoCore();
        if (instruction != null && instruction.getTipoDestino() == TipoDestinoPagoMasivoEnum.ON_US) {
            Map<String, Object> eventPayload = new LinkedHashMap<>();
            eventPayload.put("correlationId", tx.getUuidCorrelacion());
            eventPayload.put("transactionUuid", tx.getUuidTransaccion());
            eventPayload.put("paymentLineUuid", instruction.getPaymentLineUuid());
            eventPayload.put("reservationUuid", instruction.getReservaPagoMasivo().getUuidReserva());
            eventPayload.put("beneficiaryCustomerUuid", tx.getCuenta().getUuidCliente());
            eventPayload.put("beneficiaryName", instruction.getNombreBeneficiario());
            eventPayload.put("beneficiaryEmail", instruction.getEmailBeneficiario());
            eventPayload.put("destinationAccountNumber", tx.getCuenta().getNumeroCuenta());
            eventPayload.put("companyName", instruction.getReservaPagoMasivo().getCuentaMatriz().getNombreTitularReferencia());
            eventPayload.put("amount", tx.getMonto());
            eventPayload.put("concept", instruction.getConcepto());
            eventPayload.put("accountingDate", tx.getFechaContable().toString());
            eventPayload.put("receiptNumber", tx.getNumeroComprobante());
            outboxEventService.registrar(
                    "ONUS_PAYMENT_COMPLETED",
                    "INSTRUCCION_PAGO_MASIVO_CORE",
                    instruction.getPaymentLineUuid(),
                    tx.getUuidCorrelacion(),
                    toJson(eventPayload)
            );
        }
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
        amount = positiveMoney(amount);
        adminGrpcClient.validateTransactionSubtypeActive(subtype, type.name());
        validateMovementStatus(c, type, channel, subtype);
        if (type == TipoMovimientoCuentaEnum.DEBITO) {
            ensureAvailable(c, amount);
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
        t.setEstadoContabilizacion(EstadoContabilizacionCuentaEnum.PENDIENTE_CONTABILIDAD);
        t.setCanalOrigen(channel);
        t.setReferenciaExterna(externalRef);
        t.setNumeroComprobante("CMP-" + System.currentTimeMillis());
        t.setFechaCreacion(LocalDateTime.now());
        cuentaRepository.save(c);
        TransaccionCuenta saved = transaccionRepository.saveAndFlush(t);
        outboxEventService.registrar("ACCOUNT_TRANSACTION_APPLIED", "TRANSACCION_CUENTA", saved.getUuidTransaccion(), "{\"accountNumber\":\"" + c.getNumeroCuenta() + "\"}");
        return saved;
    }

    private String registerOpeningDepositJournal(TransaccionCuenta tx) {
        List<Map<String, Object>> lines = List.of(
                line(null, "BOVEDA_CENTRAL", "DEBITO", tx.getMonto(), OPENING_DEPOSIT_DESCRIPTION, 1),
                line(null, "CLIENTES_PASIVO", "CREDITO", tx.getMonto(), "Abono inicial a cuenta cliente", 2));
        return accountingGrpcClient.createJournalEntry(
                journal(tx, "DEPOSITO_APERTURA_CUENTA", OPENING_DEPOSIT_DESCRIPTION, lines));
    }

    private void markAccountingSucceeded(TransaccionCuenta tx, String asientoContableUuid) {
        tx.setAsientoContableUuid(asientoContableUuid);
        tx.setEstadoContabilizacion(EstadoContabilizacionCuentaEnum.CONTABILIZADA);
        tx.setFechaContabilizacion(LocalDateTime.now());
        tx.setCodigoErrorContable(null);
        tx.setMensajeErrorContable(null);
    }

    private void markAccountingRequiresReconciliation(String transactionUuid, String code, String message) {
        if (transactionUuid == null || transactionUuid.isBlank()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            transaccionRepository.findByUuidTransaccionForUpdate(transactionUuid).ifPresent(tx -> {
                tx.setEstadoContabilizacion(EstadoContabilizacionCuentaEnum.REQUIERE_RECONCILIACION);
                tx.setCodigoErrorContable(code);
                tx.setMensajeErrorContable(message == null ? null : message.substring(0, Math.min(message.length(), 500)));
                transaccionRepository.saveAndFlush(tx);
                auditoriaService.registrar(
                        "ACCOUNTING_RECONCILIATION_REQUIRED",
                        "TRANSACCION_CUENTA",
                        tx.getUuidTransaccion(),
                        ResultadoAuditoriaAccountEnum.ERROR,
                        toJson(Map.of("code", code == null ? "UNKNOWN" : code, "message", message == null ? "" : message)));
            });
        });
    }

    private void registerOpeningDepositCompletedEvent(TransaccionCuenta tx, String customerEmail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("correlationId", tx.getUuidCorrelacion());
        payload.put("transactionUuid", tx.getUuidTransaccion());
        payload.put("accountingEntryUuid", tx.getAsientoContableUuid());
        payload.put("accountUuid", tx.getCuenta().getUuidCuenta());
        payload.put("accountNumber", tx.getCuenta().getNumeroCuenta());
        payload.put("customerUuid", tx.getCuenta().getUuidCliente());
        payload.put("customerEmail", customerEmail);
        payload.put("holderName", tx.getCuenta().getNombreTitularReferencia());
        payload.put("amount", tx.getMonto());
        payload.put("movementType", tx.getTipoMovimiento().name());
        payload.put("operationName", "APERTURA_CUENTA");
        payload.put("description", OPENING_DEPOSIT_DESCRIPTION);
        payload.put("receiptNumber", tx.getNumeroComprobante());
        payload.put("accountingDate", tx.getFechaContable().toString());
        payload.put("resultingAccountingBalance", tx.getSaldoContableResultante());
        payload.put("resultingAvailableBalance", tx.getSaldoDisponibleResultante());
        outboxEventService.registrar(
                "ACCOUNT_OPENING_FUNDED",
                "TRANSACCION_CUENTA",
                tx.getUuidTransaccion(),
                tx.getUuidCorrelacion(),
                toJson(payload));
    }

    private String registerCashJournal(TransaccionCuenta tx, boolean deposit) {
        List<Map<String, Object>> lines = deposit
                ? List.of(line(null, "BOVEDA_CENTRAL", "DEBITO", tx.getMonto(), "Ingreso de efectivo", 1), line(null, "CLIENTES_PASIVO", "CREDITO", tx.getMonto(), "Abono a cuenta cliente", 2))
                : List.of(line(null, "CLIENTES_PASIVO", "DEBITO", tx.getMonto(), "Retiro de cuenta cliente", 1), line(null, "BOVEDA_CENTRAL", "CREDITO", tx.getMonto(), "Salida de efectivo", 2));
        return accountingGrpcClient.createJournalEntry(journal(tx, deposit ? "DEPOSITO_EFECTIVO" : "RETIRO_EFECTIVO", deposit ? "Depósito en efectivo" : "Retiro en efectivo", lines));
    }

    private void registerTellerCompletedEvent(TransaccionCuenta tx, String eventType, String operationName, String customerEmail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("correlationId", tx.getUuidCorrelacion());
        payload.put("transactionUuid", tx.getUuidTransaccion());
        payload.put("accountingEntryUuid", tx.getAsientoContableUuid());
        payload.put("accountNumber", tx.getCuenta().getNumeroCuenta());
        payload.put("customerUuid", tx.getCuenta().getUuidCliente());
        payload.put("customerEmail", customerEmail);
        payload.put("holderName", tx.getCuenta().getNombreTitularReferencia());
        payload.put("amount", tx.getMonto());
        payload.put("movementType", tx.getTipoMovimiento().name());
        payload.put("operationName", operationName);
        payload.put("receiptNumber", tx.getNumeroComprobante());
        payload.put("accountingDate", tx.getFechaContable().toString());
        payload.put("resultingAccountingBalance", tx.getSaldoContableResultante());
        payload.put("resultingAvailableBalance", tx.getSaldoDisponibleResultante());
        outboxEventService.registrar(
                eventType,
                "TRANSACCION_CUENTA",
                tx.getUuidTransaccion(),
                tx.getUuidCorrelacion(),
                toJson(payload)
        );
    }

    private String registerP2PJournal(TransaccionCuenta debit, TransaccionCuenta credit) {
        List<Map<String, Object>> lines = List.of(
                line(null, "CLIENTES_PASIVO", "DEBITO", debit.getMonto(), "Débito cuenta origen " + debit.getCuenta().getNumeroCuenta(), 1),
                line(null, "CLIENTES_PASIVO", "CREDITO", credit.getMonto(), "Crédito cuenta destino " + credit.getCuenta().getNumeroCuenta(), 2));
        return accountingGrpcClient.createJournalEntry(journal(debit, "TRANSFERENCIA_P2P", "Transferencia interna P2P", lines));
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

    private String registerGenericClientJournal(TransaccionCuenta tx, String operationType, String description) {
        String debit = tx.getTipoMovimiento() == TipoMovimientoCuentaEnum.DEBITO ? "CLIENTES_PASIVO" : "BOVEDA_CENTRAL";
        String credit = tx.getTipoMovimiento() == TipoMovimientoCuentaEnum.DEBITO ? "BOVEDA_CENTRAL" : "CLIENTES_PASIVO";
        List<Map<String, Object>> lines = List.of(
                line(null, debit, "DEBITO", tx.getMonto(), description, 1),
                line(null, credit, "CREDITO", tx.getMonto(), description, 2));
        return accountingGrpcClient.createJournalEntry(journal(tx, operationType, description, lines));
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

    private void validateAccountProductEligibility(AccountSubtypeResponse product,
                                                   CustomerBasicGrpcResponse customer,
                                                   PropositoCuentaEnum purpose,
                                                   CreateAccountRequest request) {
        validatePaymentSettingsEligibility(
                product,
                customer,
                purpose,
                Boolean.TRUE.equals(request.favoritePaymentAccount()),
                Boolean.TRUE.equals(request.massPaymentMainAccount()));
    }

    private void validateMovementStatus(Cuenta account,
                                        TipoMovimientoCuentaEnum movementType,
                                        CanalOrigenCuentaEnum channel,
                                        String subtype) {
        EstadoCuentaEnum status = account.getEstado();
        if (movementType == TipoMovimientoCuentaEnum.DEBITO) {
            if (status != EstadoCuentaEnum.ACTIVA) {
                throw new BusinessException(
                        "ACCOUNT_NOT_ACTIVE",
                        "La cuenta debe estar activa para realizar movimientos de salida",
                        HttpStatus.CONFLICT);
            }
            return;
        }

        boolean cashDeposit = channel == CanalOrigenCuentaEnum.VENTANILLA
                && "DEP_VENTANILLA".equals(subtype);
        if (cashDeposit && EnumSet.of(
                EstadoCuentaEnum.ACTIVA,
                EstadoCuentaEnum.INACTIVA,
                EstadoCuentaEnum.BLOQUEADA).contains(status)) {
            return;
        }
        if (status == EstadoCuentaEnum.ACTIVA) return;

        String code = status == EstadoCuentaEnum.SUSPENDIDA
                ? "ACCOUNT_SUSPENDED"
                : status == EstadoCuentaEnum.CERRADA
                ? "ACCOUNT_CLOSED"
                : "ACCOUNT_CREDIT_NOT_ALLOWED_BY_STATUS";
        String message = status == EstadoCuentaEnum.SUSPENDIDA
                ? "La cuenta suspendida no admite movimientos"
                : status == EstadoCuentaEnum.CERRADA
                ? "La cuenta cerrada no admite movimientos"
                : "La cuenta debe estar activa para recibir este tipo de crédito";
        throw new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    private void validateMassPaymentOverdraftSettings(Cuenta account,
                                                       AccountSubtypeResponse product,
                                                       CustomerBasicGrpcResponse customer,
                                                       boolean massPaymentMainAccount,
                                                       boolean overdraftAllowed,
                                                       BigDecimal overdraftLimit) {
        BigDecimal normalizedLimit = nonNegative(overdraftLimit);
        BigDecimal currentExposure = account.getSaldoDisponible().min(account.getSaldoContable()).min(ZERO).abs();

        if (!overdraftAllowed) {
            if (normalizedLimit.compareTo(ZERO) != 0) {
                throw new BusinessException(
                        "ACCOUNT_OVERDRAFT_LIMIT_REQUIRES_ENABLEMENT",
                        "El límite de sobregiro debe ser cero cuando el sobregiro está deshabilitado",
                        HttpStatus.BAD_REQUEST);
            }
            if (currentExposure.compareTo(ZERO) > 0) {
                throw new BusinessException(
                        "ACCOUNT_OVERDRAFT_IN_USE",
                        "No puede deshabilitarse el sobregiro mientras la cuenta mantiene un saldo negativo",
                        HttpStatus.CONFLICT);
            }
            return;
        }

        if (account.getEstado() != EstadoCuentaEnum.ACTIVA) {
            throw new BusinessException(
                    "ACCOUNT_OVERDRAFT_REQUIRES_ACTIVE_ACCOUNT",
                    "El sobregiro para comisión solo puede configurarse en una cuenta activa",
                    HttpStatus.CONFLICT);
        }
        if (!massPaymentMainAccount) {
            throw new BusinessException(
                    "ACCOUNT_OVERDRAFT_REQUIRES_MASS_PAYMENT_MAIN",
                    "El sobregiro para comisión requiere que la cuenta sea matriz de pagos masivos",
                    HttpStatus.CONFLICT);
        }
        if (!"JURIDICO".equalsIgnoreCase(customer.getCustomerType())) {
            throw new BusinessException(
                    "ACCOUNT_OVERDRAFT_ONLY_LEGAL_CUSTOMER",
                    "El sobregiro para comisión solo aplica a cuentas empresariales",
                    HttpStatus.CONFLICT);
        }
        if (!"CORRIENTE".equalsIgnoreCase(product.getBaseType())) {
            throw new BusinessException(
                    "ACCOUNT_OVERDRAFT_ONLY_CHECKING",
                    "El sobregiro para comisión solo puede habilitarse en cuentas corrientes",
                    HttpStatus.CONFLICT);
        }
        if (!product.getSupportsMassPayments()) {
            throw new BusinessException(
                    "ACCOUNT_PRODUCT_MASS_PAYMENTS_NOT_SUPPORTED",
                    "El producto no admite pagos masivos",
                    HttpStatus.CONFLICT);
        }
        if (normalizedLimit.compareTo(ZERO) <= 0) {
            throw new BusinessException(
                    "ACCOUNT_OVERDRAFT_LIMIT_REQUIRED",
                    "El límite autorizado debe ser mayor a cero",
                    HttpStatus.BAD_REQUEST);
        }
        if (currentExposure.compareTo(normalizedLimit) > 0) {
            throw new BusinessException(
                    "ACCOUNT_OVERDRAFT_LIMIT_BELOW_EXPOSURE",
                    "El límite no puede ser menor al sobregiro actualmente utilizado",
                    HttpStatus.CONFLICT);
        }
    }

    private void validatePaymentSettingsEligibility(AccountSubtypeResponse product,
                                                    CustomerBasicGrpcResponse customer,
                                                    PropositoCuentaEnum purpose,
                                                    boolean favoritePaymentAccount,
                                                    boolean massPaymentMainAccount) {
        if (!product.getAllowedCustomerTypesList().isEmpty()
                && !product.getAllowedCustomerTypesList().contains(customer.getCustomerType())) {
            throw new BusinessException("ACCOUNT_PRODUCT_CUSTOMER_TYPE_NOT_ALLOWED",
                    "El producto no está habilitado para el tipo de cliente", HttpStatus.CONFLICT);
        }
        if (!product.getAllowedPurposesList().isEmpty()
                && !product.getAllowedPurposesList().contains(purpose.name())) {
            throw new BusinessException("ACCOUNT_PRODUCT_PURPOSE_NOT_ALLOWED",
                    "El producto no admite el propósito de cuenta solicitado", HttpStatus.CONFLICT);
        }
        if (favoritePaymentAccount && !product.getSupportsFavoritePaymentAccount()) {
            throw new BusinessException("ACCOUNT_PRODUCT_FAVORITE_NOT_SUPPORTED",
                    "El producto no puede utilizarse como cuenta favorita de pagos", HttpStatus.CONFLICT);
        }
        if (!massPaymentMainAccount) return;
        if (!"JURIDICO".equalsIgnoreCase(customer.getCustomerType())) {
            throw new BusinessException("ACCOUNT_MASS_PAYMENT_MAIN_ONLY_LEGAL",
                    "Solo una cuenta de persona jurídica puede habilitarse para pagos masivos", HttpStatus.CONFLICT);
        }
        if (!customer.getMassPaymentsEnabled()) {
            throw new BusinessException("ACCOUNT_MASS_PAYMENTS_DISABLED",
                    "La empresa no tiene habilitado el servicio de pagos masivos", HttpStatus.CONFLICT);
        }
        if (!product.getSupportsMassPayments()) {
            throw new BusinessException("ACCOUNT_PRODUCT_MASS_PAYMENTS_NOT_SUPPORTED",
                    "El producto no admite pagos masivos", HttpStatus.CONFLICT);
        }
    }

    private void validateStatusTransition(EstadoCuentaEnum current, EstadoCuentaEnum next) {
        if (current == EstadoCuentaEnum.CERRADA) {
            throw new BusinessException(
                    "ACCOUNT_STATUS_TRANSITION_NOT_ALLOWED",
                    "Una cuenta cerrada no admite cambios de estado",
                    HttpStatus.CONFLICT);
        }
        Set<EstadoCuentaEnum> allowed = switch (current) {
            case ACTIVA -> EnumSet.of(EstadoCuentaEnum.INACTIVA, EstadoCuentaEnum.BLOQUEADA, EstadoCuentaEnum.SUSPENDIDA);
            case INACTIVA -> EnumSet.of(EstadoCuentaEnum.ACTIVA, EstadoCuentaEnum.BLOQUEADA, EstadoCuentaEnum.SUSPENDIDA);
            case BLOQUEADA -> EnumSet.of(EstadoCuentaEnum.ACTIVA, EstadoCuentaEnum.INACTIVA, EstadoCuentaEnum.SUSPENDIDA);
            case SUSPENDIDA -> EnumSet.of(EstadoCuentaEnum.ACTIVA, EstadoCuentaEnum.INACTIVA, EstadoCuentaEnum.BLOQUEADA);
            case CERRADA -> EnumSet.noneOf(EstadoCuentaEnum.class);
        };
        if (!allowed.contains(next)) {
            throw new BusinessException(
                    "ACCOUNT_STATUS_TRANSITION_NOT_ALLOWED",
                    "La transición de estado solicitada no está permitida",
                    HttpStatus.CONFLICT);
        }
    }

    private void validateBlockExpiration(LocalDateTime expiration) {
        if (expiration != null && !expiration.isAfter(LocalDateTime.now())) {
            throw new BusinessException(
                    "ACCOUNT_BLOCK_EXPIRATION_INVALID",
                    "La fecha de expiración debe ser posterior al momento actual",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> blockEventPayload(BloqueoCuenta block, String action, String actorUuid) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("blockUuid", block.getUuidBloqueo());
        payload.put("accountNumber", block.getCuenta().getNumeroCuenta());
        payload.put("amount", block.getMontoBloqueado());
        payload.put("status", block.getEstado().name());
        payload.put("reason", block.getMotivo());
        payload.put("orderingAuthority", block.getAutoridadOrdenante());
        payload.put("actorUuid", actorUuid == null ? "SYSTEM" : normalizeActor(actorUuid));
        payload.put("blockedAt", block.getFechaBloqueo());
        payload.put("expiresAt", block.getFechaExpiracion());
        payload.put("releasedAt", block.getFechaLiberacion());
        return payload;
    }

    private void recalculateAvailableBalance(Cuenta account) {
        BigDecimal accountingBalance = (account.getSaldoContable() == null ? ZERO : account.getSaldoContable())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal withheldAmount = nonNegative(account.getMontoRetenido());
        BigDecimal availableBalance = accountingBalance.subtract(withheldAmount);
        BigDecimal minimumAllowed = Boolean.TRUE.equals(account.getPermiteSobregiro())
                ? account.getLimiteSobregiro().negate()
                : ZERO;
        if (availableBalance.compareTo(minimumAllowed) < 0) {
            throw new BusinessException(
                    "ACCOUNT_BALANCE_INVARIANT_VIOLATION",
                    "El saldo disponible no puede ser inferior al límite permitido",
                    HttpStatus.CONFLICT);
        }
        account.setSaldoDisponible(availableBalance.setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.compareTo(ZERO) < 0) return ZERO.setScale(2, RoundingMode.HALF_UP);
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public AccountingReconciliationRunResponse runAccountingReconciliation() {
        List<TransaccionCuenta> candidates = transaccionRepository
                .findTop50ByEstadoContabilizacionInOrderByFechaCreacionAsc(List.of(
                        EstadoContabilizacionCuentaEnum.PENDIENTE_CONTABILIDAD,
                        EstadoContabilizacionCuentaEnum.REQUIERE_RECONCILIACION));
        int reconciled = 0;
        int stillPending = 0;
        int manualReview = 0;
        for (TransaccionCuenta tx : candidates) {
            try {
                Optional<String> journalUuid = accountingGrpcClient.findJournalEntryByTransactionUuid(tx.getUuidTransaccion());
                if (journalUuid.isPresent()) {
                    markAccountingSucceeded(tx, journalUuid.get());
                    transaccionRepository.saveAndFlush(tx);
                    auditoriaService.registrar(
                            "ACCOUNTING_RECONCILIATION_MATCHED",
                            "TRANSACCION_CUENTA",
                            tx.getUuidTransaccion(),
                            ResultadoAuditoriaAccountEnum.OK,
                            toJson(Map.of("journalEntryUuid", journalUuid.get())));
                    reconciled++;
                } else {
                    tx.setEstadoContabilizacion(EstadoContabilizacionCuentaEnum.REQUIERE_RECONCILIACION);
                    tx.setCodigoErrorContable("ACCOUNTING_JOURNAL_NOT_FOUND");
                    tx.setMensajeErrorContable("No existe asiento contable para la transacción; requiere revisión o compensación controlada");
                    transaccionRepository.saveAndFlush(tx);
                    manualReview++;
                }
            } catch (BusinessException exception) {
                tx.setEstadoContabilizacion(EstadoContabilizacionCuentaEnum.REQUIERE_RECONCILIACION);
                tx.setCodigoErrorContable(exception.getCode());
                tx.setMensajeErrorContable(exception.getMessage());
                transaccionRepository.saveAndFlush(tx);
                stillPending++;
            }
        }
        return new AccountingReconciliationRunResponse(candidates.size(), reconciled, stillPending, manualReview);
    }

    private String normalizeActor(String actorUuid) {
        if (actorUuid == null || actorUuid.isBlank()) {
            throw new BusinessException(
                    "ACCOUNT_AUTHENTICATED_ACTOR_REQUIRED",
                    "No fue posible identificar al actor autenticado",
                    HttpStatus.UNAUTHORIZED);
        }
        return actorUuid.trim();
    }

    private AccountTransactionResponse toTransactionResponse(TransaccionCuenta transaction) {
        return AccountMapper.toTransaction(
                transaction,
                adminGrpcClient.getTransactionSubtypeName(transaction.getCodigoSubtipoTransaccion()));
    }

    private List<AccountTransactionResponse> toTransactionResponses(List<TransaccionCuenta> transactions) {
        Map<String, String> subtypeNames = new HashMap<>();
        return transactions.stream()
                .map(transaction -> AccountMapper.toTransaction(
                        transaction,
                        subtypeNames.computeIfAbsent(
                                transaction.getCodigoSubtipoTransaccion(),
                                adminGrpcClient::getTransactionSubtypeName)))
                .toList();
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) return null;
        String normalized = accountNumber.trim();
        int visibleDigits = Math.min(4, normalized.length());
        return "•".repeat(normalized.length() - visibleDigits)
                + normalized.substring(normalized.length() - visibleDigits);
    }

    private Cuenta findAccount(String number) { return cuentaRepository.findByNumeroCuenta(number).orElseThrow(() -> notFound("ACCOUNT_NOT_FOUND", "Cuenta no encontrada")); }
    private Cuenta findActiveAccount(String number) { Cuenta c = findAccount(number); if (c.getEstado() != EstadoCuentaEnum.ACTIVA) throw new BusinessException("ACCOUNT_NOT_ACTIVE", "La cuenta no está activa", HttpStatus.CONFLICT); return c; }
    private ReservaPagoMasivo findReservation(String uuid) { return reservaRepository.findByUuidReserva(uuid).orElseThrow(() -> notFound("ACCOUNT_RESERVATION_NOT_FOUND", "Reserva no encontrada")); }
    private BusinessException notFound(String code, String msg) { return new BusinessException(code, msg, HttpStatus.NOT_FOUND); }
    private void ensureAvailable(Cuenta c, BigDecimal amount) { if (c.getSaldoDisponible().compareTo(amount) < 0) throw new BusinessException("ACCOUNT_INSUFFICIENT_FUNDS", "Saldo disponible insuficiente", HttpStatus.CONFLICT); }
    private BigDecimal money(BigDecimal value) {
        if (value == null || value.compareTo(ZERO) < 0) {
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
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return accountingGrpcClient.resolveOperationAccountingDate();
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    "ACCOUNT_INVALID_ACCOUNTING_DATE",
                    "La fecha contable debe usar el formato yyyy-MM-dd",
                    HttpStatus.BAD_REQUEST);
        }
    }
    private void clearFavorite(String customerUuid) { cuentaRepository.findByUuidClienteAndEsCuentaFavoritaPagos(customerUuid, true).ifPresent(c -> { c.setEsCuentaFavoritaPagos(false); cuentaRepository.save(c); }); }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible serializar el evento de integración", exception);
        }
    }

    private String normalizeRequired(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }
    private String generateAccountNumber(String branchCode) { return branchCode + String.format("%010d", System.currentTimeMillis() % 10000000000L); }
    private String defaultString(String value, String defaultValue) { return value == null || value.isBlank() ? defaultValue : value; }

    private CanalOrigenCuentaEnum tellerChannel(String value) {
        CanalOrigenCuentaEnum channel = value == null || value.isBlank()
                ? CanalOrigenCuentaEnum.VENTANILLA
                : parseEnum(value, CanalOrigenCuentaEnum.class,
                        "ACCOUNT_TELLER_CHANNEL_INVALID",
                        "Las operaciones de Teller deben utilizar el canal VENTANILLA");
        if (channel != CanalOrigenCuentaEnum.VENTANILLA) {
            throw new BusinessException(
                    "ACCOUNT_TELLER_CHANNEL_INVALID",
                    "Las operaciones de Teller deben utilizar el canal VENTANILLA",
                    HttpStatus.BAD_REQUEST);
        }
        return channel;
    }

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
