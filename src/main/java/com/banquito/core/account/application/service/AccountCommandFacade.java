package com.banquito.core.account.application.service;

import com.banquito.core.account.api.dto.api.AccountMovementRequest;
import com.banquito.core.account.api.dto.api.AccountResponse;
import com.banquito.core.account.api.dto.api.AccountTransactionResponse;
import com.banquito.core.account.api.dto.api.CreateAccountRequest;
import com.banquito.core.account.api.dto.internal.IdempotentOperationResponse;
import com.banquito.core.account.shared.tracing.CorrelationIdHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountCommandFacade {

    private static final String CREATE_ACCOUNT = "APERTURA_CUENTA";
    private static final String TELLER_DEPOSIT = "DEPOSITO_VENTANILLA";
    private static final String TELLER_WITHDRAWAL = "RETIRO_VENTANILLA";

    private final AccountService accountService;
    private final AccountOperationIdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;

    public AccountCommandFacade(AccountService accountService,
                                AccountOperationIdempotencyService idempotencyService,
                                PlatformTransactionManager transactionManager) {
        this.accountService = accountService;
        this.idempotencyService = idempotencyService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public IdempotentOperationResponse<AccountResponse> createAccount(
            CreateAccountRequest request, String idempotencyKey, String actorUuid) {
        var start = idempotencyService.begin(actorUuid, CREATE_ACCOUNT, idempotencyKey,
                CorrelationIdHolder.get(), request);
        if (start.replayed()) {
            return new IdempotentOperationResponse<>(
                    idempotencyService.deserialize(start.responseJson(), AccountResponse.class),
                    true,
                    statusOrDefault(start.httpStatus(), HttpStatus.CREATED.value()));
        }
        try {
            AccountResponse response = transactionTemplate.execute(status -> {
                AccountResponse result = accountService.createAccount(request);
                idempotencyService.complete(start.recordId(), HttpStatus.CREATED.value(), result);
                return result;
            });
            if (response == null) throw new IllegalStateException("La apertura de cuenta no generó respuesta");
            return new IdempotentOperationResponse<>(response, false, HttpStatus.CREATED.value());
        } catch (RuntimeException exception) {
            idempotencyService.fail(start.recordId(), exception);
            throw exception;
        }
    }

    public IdempotentOperationResponse<AccountTransactionResponse> deposit(
            AccountMovementRequest request, String idempotencyKey, String actorUuid) {
        return executeMovement(request, idempotencyKey, actorUuid, TELLER_DEPOSIT, true);
    }

    public IdempotentOperationResponse<AccountTransactionResponse> withdraw(
            AccountMovementRequest request, String idempotencyKey, String actorUuid) {
        return executeMovement(request, idempotencyKey, actorUuid, TELLER_WITHDRAWAL, false);
    }

    private IdempotentOperationResponse<AccountTransactionResponse> executeMovement(
            AccountMovementRequest request, String idempotencyKey, String actorUuid,
            String operationType, boolean deposit) {
        var start = idempotencyService.begin(actorUuid, operationType, idempotencyKey,
                request.correlationId(), request);
        if (start.replayed()) {
            return new IdempotentOperationResponse<>(
                    idempotencyService.deserialize(start.responseJson(), AccountTransactionResponse.class),
                    true,
                    statusOrDefault(start.httpStatus(), HttpStatus.OK.value()));
        }
        try {
            AccountTransactionResponse response = transactionTemplate.execute(status -> {
                AccountTransactionResponse result = deposit
                        ? accountService.deposit(request)
                        : accountService.withdraw(request);
                idempotencyService.complete(start.recordId(), HttpStatus.OK.value(), result);
                return result;
            });
            if (response == null) throw new IllegalStateException("La operación de ventanilla no generó respuesta");
            return new IdempotentOperationResponse<>(response, false, HttpStatus.OK.value());
        } catch (RuntimeException exception) {
            idempotencyService.fail(start.recordId(), exception);
            throw exception;
        }
    }

    private int statusOrDefault(Integer status, int defaultStatus) {
        return status == null ? defaultStatus : status;
    }
}
