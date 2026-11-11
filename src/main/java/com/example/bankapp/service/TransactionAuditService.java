package com.example.bankapp.service;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransactionAuditService {

    private final BankTransactionRepository transactionRepository;

    // REQUIRES_NEW: Always runs in its own independent transaction,
    // so it commits even if the parent (transfer) transaction rolls back.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String fromAccount, String toAccount, BigDecimal amount, String description) {
        BankTransaction tx = new BankTransaction(
                fromAccount,
                toAccount,
                amount,
                TransactionStatus.FAILED,
                description
        );
        transactionRepository.save(tx);
    }
}