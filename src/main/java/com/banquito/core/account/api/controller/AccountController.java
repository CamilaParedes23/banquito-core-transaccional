package com.banquito.core.account.api.controller;

import com.banquito.core.account.api.dto.api.*;
import com.banquito.core.account.application.service.AccountService;
import com.banquito.core.account.application.service.AccountCommandFacade;
import com.banquito.core.account.application.service.P2PTransferFacade;
import com.banquito.core.account.api.dto.internal.AuthenticatedActor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final AccountService service;
    private final P2PTransferFacade p2pTransferFacade;
    private final AccountCommandFacade accountCommandFacade;

    public AccountController(AccountService service, P2PTransferFacade p2pTransferFacade,
                             AccountCommandFacade accountCommandFacade) {
        this.service = service;
        this.p2pTransferFacade = p2pTransferFacade;
        this.accountCommandFacade = accountCommandFacade;
    }

    @PostMapping("/accounts")
    @PreAuthorize("@accountAccessPolicy.canBackoffice(authentication)")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        var result = accountCommandFacade.createAccount(request, idempotencyKey, actorKey(authentication));
        return ResponseEntity.status(result.httpStatus())
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.body());
    }
    @GetMapping("/accounts")
    @PreAuthorize("@accountAccessPolicy.canBackoffice(authentication)")
    public AccountListResponse listAccounts(@RequestParam(required=false) String status, @RequestParam(required=false) String subtypeCode, @RequestParam(required=false) String branchCode, @RequestParam(required=false) String accountPurpose, @RequestParam(required=false) String search, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return service.listAccounts(status, subtypeCode, branchCode, accountPurpose, search, page, size); }
    @GetMapping("/accounts/all")
    @PreAuthorize("@accountAccessPolicy.canBackoffice(authentication)")
    public List<AccountResponse> getAllAccounts(@RequestParam(required=false) String status) { return service.listAllAccounts(status); }
    @GetMapping("/accounts/by-customer-identification/{identification}")
    @PreAuthorize("@accountAccessPolicy.canReadIdentification(authentication, #p0)")
    public List<AccountResponse> getAccountsByCustomerIdentification(@PathVariable String identification) { return service.listByCustomerIdentification(identification); }
    @GetMapping("/accounts/{accountNumber}")
    @PreAuthorize("@accountAccessPolicy.canReadAccount(authentication, #p0)")
    public AccountResponse getAccount(@PathVariable String accountNumber) { return service.getByNumber(accountNumber); }
    @GetMapping("/accounts/{accountNumber}/balance")
    @PreAuthorize("@accountAccessPolicy.canReadAccount(authentication, #p0)")
    public BalanceResponse getBalance(@PathVariable String accountNumber) { return service.getBalance(accountNumber); }
    @GetMapping("/accounts/{accountNumber}/transactions")
    @PreAuthorize("@accountAccessPolicy.canReadAccount(authentication, #p0)")
    public List<AccountTransactionResponse> getTransactions(@PathVariable String accountNumber) { return service.getTransactions(accountNumber); }
    @GetMapping("/accounts/transactions/{transactionUuid}")
    @PreAuthorize("@accountAccessPolicy.canBackoffice(authentication)")
    public AccountTransactionResponse getTransaction(@PathVariable String transactionUuid) {
        return service.getTransaction(transactionUuid);
    }
    @GetMapping("/accounts/by-customer/{customerUuid}")
    @PreAuthorize("@accountAccessPolicy.canReadCustomer(authentication, #p0)")
    public List<AccountResponse> getAccountsByCustomer(@PathVariable String customerUuid, @RequestParam(required=false) String status, @RequestParam(required=false) Boolean onlyTransferable, @RequestParam(required=false) String purpose, @RequestParam(required=false) Boolean includeBalance) { return service.listByCustomer(customerUuid, status, onlyTransferable, purpose, includeBalance); }
    @PatchMapping("/accounts/{accountNumber}/status")
    @PreAuthorize("@accountAccessPolicy.canBackoffice(authentication)")
    public AccountResponse updateStatus(@PathVariable String accountNumber, @Valid @RequestBody UpdateAccountStatusRequest request) { return service.updateStatus(accountNumber, request); }
    @PatchMapping("/accounts/{accountNumber}/payment-settings")
    @PreAuthorize("@accountAccessPolicy.canBackoffice(authentication)")
    public AccountResponse updatePaymentSettings(@PathVariable String accountNumber, @RequestBody UpdatePaymentSettingsRequest request) { return service.updatePaymentSettings(accountNumber, request); }
    @PostMapping("/accounts/{accountNumber}/blocks")
    @PreAuthorize("@accountAccessPolicy.canBackoffice(authentication)")
    public BlockResponse block(@PathVariable String accountNumber, @Valid @RequestBody BlockAccountRequest request) { return service.block(accountNumber, request); }
    @PatchMapping("/accounts/{accountNumber}/blocks/{blockUuid}/release")
    @PreAuthorize("@accountAccessPolicy.canBackoffice(authentication)")
    public BlockResponse releaseBlock(@PathVariable String accountNumber, @PathVariable String blockUuid) { return service.releaseBlock(accountNumber, blockUuid); }
    @PostMapping("/teller/deposits")
    @PreAuthorize("@accountAccessPolicy.canBackoffice(authentication)")
    public ResponseEntity<AccountTransactionResponse> deposit(
            @Valid @RequestBody AccountMovementRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        var result = accountCommandFacade.deposit(request, idempotencyKey, actorKey(authentication));
        return ResponseEntity.status(result.httpStatus())
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.body());
    }
    @PostMapping("/teller/withdrawals")
    @PreAuthorize("@accountAccessPolicy.canBackoffice(authentication)")
    public ResponseEntity<AccountTransactionResponse> withdrawal(
            @Valid @RequestBody AccountMovementRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        var result = accountCommandFacade.withdraw(request, idempotencyKey, actorKey(authentication));
        return ResponseEntity.status(result.httpStatus())
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.body());
    }
    @PostMapping("/accounts/transfers/p2p/beneficiary-validation")
    @PreAuthorize("@accountAccessPolicy.canValidateP2pBeneficiary(authentication)")
    public P2PBeneficiaryValidationResponse validateP2pBeneficiary(
            @Valid @RequestBody P2PBeneficiaryValidationRequest request) {
        return service.validateP2PBeneficiary(request.accountNumber());
    }

    @PostMapping("/accounts/transfers/p2p")
    @PreAuthorize("@accountAccessPolicy.canTransferP2p(authentication, #p0.sourceAccountNumber())")
    public ResponseEntity<List<AccountTransactionResponse>> p2p(
            @Valid @RequestBody P2PTransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        var result = p2pTransferFacade.execute(request, idempotencyKey, actorKey(authentication));
        return ResponseEntity.ok()
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.transactions());
    }
    @PostMapping("/accounts/transactions/{transactionUuid}/reverse")
    @PreAuthorize("@accountAccessPolicy.canBackoffice(authentication)")
    public AccountTransactionResponse reverse(@PathVariable String transactionUuid,
                                              @Valid @RequestBody ReverseTransactionRequest request) {
        return service.reverseTransaction(transactionUuid, request);
    }
    @PostMapping("/switch-core/payment-reservations")
    @PreAuthorize("@accountAccessPolicy.canCreateReservation(authentication, #p0.companyCustomerUuid(), #p0.mainAccountNumber())")
    public ReservationResponse createReservation(@Valid @RequestBody ReservationRequest request) { return service.createReservation(request); }
    @PostMapping("/switch-core/payment-reservations/{reservationUuid}/consume")
    @PreAuthorize("@accountAccessPolicy.canConsumeReservation(authentication, #p0)")
    public ReservationResponse consumeReservation(@PathVariable String reservationUuid, @Valid @RequestBody ConsumeReservationRequest request) { return service.consumeReservation(reservationUuid, request); }
    @PostMapping("/switch-core/payment-reservations/{reservationUuid}/release")
    @PreAuthorize("@accountAccessPolicy.canReleaseReservation(authentication, #p0)")
    public ReservationResponse releaseReservation(@PathVariable String reservationUuid) { return service.releaseReservation(reservationUuid); }
    @PostMapping("/switch-core/payment-reservations/{reservationUuid}/reverse")
    @PreAuthorize("@accountAccessPolicy.canManageReservation(authentication, #p0)")
    public ReservationResponse reverseReservation(@PathVariable String reservationUuid) { return service.reverseReservation(reservationUuid); }
    @PostMapping("/switch-core/payment-reservations/{reservationUuid}/close")
    @PreAuthorize("@accountAccessPolicy.canManageReservation(authentication, #p0)")
    public ReservationResponse closeReservation(@PathVariable String reservationUuid) { return service.closeReservation(reservationUuid); }
    @PostMapping("/switch-core/payment-reservations/{reservationUuid}/service-fee-charge")
    @PreAuthorize("@accountAccessPolicy.canManageReservation(authentication, #p0)")
    public ReservationResponse chargeServiceFee(@PathVariable String reservationUuid, @RequestBody FeeChargeRequest request) { return service.chargeServiceFee(reservationUuid, request); }

    private String actorKey(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedActor actor)) {
            return authentication == null ? null : authentication.getName();
        }
        return actor.actorUuid() != null && !actor.actorUuid().isBlank()
                ? actor.actorUuid()
                : actor.username();
    }
}
