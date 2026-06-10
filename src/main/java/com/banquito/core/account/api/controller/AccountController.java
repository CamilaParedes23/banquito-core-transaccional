package com.banquito.core.account.api.controller;

import com.banquito.core.account.api.dto.api.*;
import com.banquito.core.account.application.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }

    @PostMapping("/accounts")
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) { return service.createAccount(request); }
    @GetMapping("/accounts")
    public AccountListResponse listAccounts(@RequestParam(required=false) String status, @RequestParam(required=false) String subtypeCode, @RequestParam(required=false) String branchCode, @RequestParam(required=false) String accountPurpose, @RequestParam(required=false) String search, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return service.listAccounts(status, subtypeCode, branchCode, accountPurpose, search, page, size); }
    @GetMapping("/accounts/all")
    public List<AccountResponse> getAllAccounts(@RequestParam(required=false) String status) { return service.listAllAccounts(status); }
    @GetMapping("/accounts/by-customer-identification/{identification}")
    public List<AccountResponse> getAccountsByCustomerIdentification(@PathVariable String identification) { return service.listByCustomerIdentification(identification); }
    @GetMapping("/accounts/{accountNumber}")
    public AccountResponse getAccount(@PathVariable String accountNumber) { return service.getByNumber(accountNumber); }
    @GetMapping("/accounts/{accountNumber}/balance")
    public BalanceResponse getBalance(@PathVariable String accountNumber) { return service.getBalance(accountNumber); }
    @GetMapping("/accounts/{accountNumber}/transactions")
    public List<AccountTransactionResponse> getTransactions(@PathVariable String accountNumber) { return service.getTransactions(accountNumber); }
    @GetMapping("/accounts/by-customer/{customerUuid}")
    public List<AccountResponse> getAccountsByCustomer(@PathVariable String customerUuid, @RequestParam(required=false) String status, @RequestParam(required=false) Boolean onlyTransferable, @RequestParam(required=false) String purpose, @RequestParam(required=false) Boolean includeBalance) { return service.listByCustomer(customerUuid, status, onlyTransferable, purpose, includeBalance); }
    @PatchMapping("/accounts/{accountNumber}/status")
    public AccountResponse updateStatus(@PathVariable String accountNumber, @Valid @RequestBody UpdateAccountStatusRequest request) { return service.updateStatus(accountNumber, request); }
    @PatchMapping("/accounts/{accountNumber}/payment-settings")
    public AccountResponse updatePaymentSettings(@PathVariable String accountNumber, @RequestBody UpdatePaymentSettingsRequest request) { return service.updatePaymentSettings(accountNumber, request); }
    @PostMapping("/accounts/{accountNumber}/blocks")
    public BlockResponse block(@PathVariable String accountNumber, @Valid @RequestBody BlockAccountRequest request) { return service.block(accountNumber, request); }
    @PatchMapping("/accounts/{accountNumber}/blocks/{blockUuid}/release")
    public BlockResponse releaseBlock(@PathVariable String accountNumber, @PathVariable String blockUuid) { return service.releaseBlock(accountNumber, blockUuid); }
    @PostMapping("/teller/deposits")
    public AccountTransactionResponse deposit(@Valid @RequestBody AccountMovementRequest request) { return service.deposit(request); }
    @PostMapping("/teller/withdrawals")
    public AccountTransactionResponse withdrawal(@Valid @RequestBody AccountMovementRequest request) { return service.withdraw(request); }
    @PostMapping("/accounts/transfers/p2p")
    public List<AccountTransactionResponse> p2p(@Valid @RequestBody P2PTransferRequest request) { return service.p2p(request); }
    @PostMapping("/accounts/transactions/{transactionUuid}/reverse")
    public AccountTransactionResponse reverse(@PathVariable String transactionUuid) { return service.reverseTransaction(transactionUuid); }
    @PostMapping("/switch-core/payment-reservations")
    public ReservationResponse createReservation(@Valid @RequestBody ReservationRequest request) { return service.createReservation(request); }
    @PostMapping("/switch-core/payment-reservations/{reservationUuid}/consume")
    public ReservationResponse consumeReservation(@PathVariable String reservationUuid, @Valid @RequestBody ConsumeReservationRequest request) { return service.consumeReservation(reservationUuid, request); }
    @PostMapping("/switch-core/payment-reservations/{reservationUuid}/release")
    public ReservationResponse releaseReservation(@PathVariable String reservationUuid) { return service.releaseReservation(reservationUuid); }
    @PostMapping("/switch-core/payment-reservations/{reservationUuid}/reverse")
    public ReservationResponse reverseReservation(@PathVariable String reservationUuid) { return service.reverseReservation(reservationUuid); }
    @PostMapping("/switch-core/payment-reservations/{reservationUuid}/close")
    public ReservationResponse closeReservation(@PathVariable String reservationUuid) { return service.closeReservation(reservationUuid); }
    @PostMapping("/switch-core/payment-reservations/{reservationUuid}/service-fee-charge")
    public ReservationResponse chargeServiceFee(@PathVariable String reservationUuid, @RequestBody FeeChargeRequest request) { return service.chargeServiceFee(reservationUuid, request); }
}
