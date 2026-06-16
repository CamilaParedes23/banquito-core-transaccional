package com.banquito.core.account.application.service;

import com.banquito.core.account.api.dto.api.AccountTransactionResponse;
import com.banquito.core.account.api.dto.api.P2PTransferRequest;
import com.banquito.core.account.api.dto.internal.IdempotentP2PResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
public class P2PTransferFacade {

    private final AccountService accountService;
    private final P2PIdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;

    public P2PTransferFacade(AccountService accountService,
                             P2PIdempotencyService idempotencyService,
                             PlatformTransactionManager transactionManager) {
        this.accountService = accountService;
        this.idempotencyService = idempotencyService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public IdempotentP2PResponse execute(P2PTransferRequest request,
                                         String idempotencyKey,
                                         String actorUuid) {
        P2PIdempotencyService.StartResult start =
                idempotencyService.begin(actorUuid, idempotencyKey, request);

        if (start.replayed()) {
            return new IdempotentP2PResponse(start.response(), true);
        }

        try {
            List<AccountTransactionResponse> response = transactionTemplate.execute(status -> {
                List<AccountTransactionResponse> result = accountService.p2p(request);
                idempotencyService.complete(start.recordId(), result);
                return result;
            });
            if (response == null) throw new IllegalStateException("La transferencia P2P no generó respuesta");
            return new IdempotentP2PResponse(response, false);
        } catch (RuntimeException exception) {
            idempotencyService.fail(start.recordId(), exception);
            throw exception;
        }
    }
}
