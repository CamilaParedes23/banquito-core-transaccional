package com.banquito.core.account.application.service;

import com.banquito.core.account.domain.model.TransaccionCuenta;
import com.banquito.core.account.domain.repository.TransaccionCuentaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionDocumentLinkService {

    private final TransaccionCuentaRepository transactionRepository;

    public TransactionDocumentLinkService(TransaccionCuentaRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public void link(String documentUuid, String... transactionUuids) {
        List<TransaccionCuenta> transactions = new ArrayList<>();
        for (String transactionUuid : transactionUuids) {
            if (transactionUuid == null || transactionUuid.isBlank()) continue;
            transactionRepository.findByUuidTransaccionForUpdate(transactionUuid).ifPresent(transaction -> {
                transaction.setUuidDocumentoComprobante(documentUuid);
                transactions.add(transaction);
            });
        }
        if (!transactions.isEmpty()) {
            transactionRepository.saveAll(transactions);
        }
    }
}
