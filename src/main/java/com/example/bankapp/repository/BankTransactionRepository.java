package com.example.bankapp.repository;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    // 1. newest first
    @Query("SELECT t FROM BankTransaction t ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findAllLatest();

    // 2. account is sender OR receiver
    @Query("SELECT t FROM BankTransaction t " +
           "WHERE t.fromAccount = :account OR t.toAccount = :account " +
           "ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findHistory(@Param("account") String account);

    // 3. enum as parameter
    @Query("SELECT t FROM BankTransaction t WHERE t.status = :status ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findByStatusLatest(@Param("status") TransactionStatus status);

    // 4. count by status (dashboard)
    @Query("SELECT COUNT(t) FROM BankTransaction t WHERE t.status = :status")
    long countByStatus(@Param("status") TransactionStatus status);

    // ---- Task 12 ----
    // transfers (both accounts present) with amount >= min, biggest first
    @Query("SELECT t FROM BankTransaction t " +
           "WHERE t.amount >= :min AND t.fromAccount IS NOT NULL AND t.toAccount IS NOT NULL " +
           "ORDER BY t.amount DESC, t.id DESC")
    List<BankTransaction> findLargeTransfers(@Param("min") BigDecimal min);

    // total of SUCCESS transfers sent by one account (null when there are none)
    @Query("SELECT SUM(t.amount) FROM BankTransaction t " +
            "WHERE t.fromAccount = :account AND t.toAccount IS NOT NULL AND t.status = :status")
    BigDecimal findTotalSent(@Param("account") String account,
                             @Param("status") TransactionStatus status);
}