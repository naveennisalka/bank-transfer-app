package com.example.bankapp.repository;

import com.example.bankapp.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // 1. Find by account number (JPQL)
    @Query("SELECT c FROM Customer c WHERE c.accountNumber = :accountNumber")
    Optional<Customer> getByAccountNumber(@Param("accountNumber") String accountNumber);

    // 2. Count accounts to check existence
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.accountNumber = :accountNumber")
    long countAccounts(@Param("accountNumber") String accountNumber);

    // Count emails to check existence
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.email = :email")
    long countEmails(@Param("email") String email);

    // 3. All customers ordered by name (JPQL)
    @Query("SELECT c FROM Customer c ORDER BY c.fullName ASC")
    List<Customer> findAllOrdered();

    // 4. Case-insensitive search by name (JPQL LIKE)
    @Query("SELECT c FROM Customer c WHERE LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR c.accountNumber LIKE CONCAT('%', :keyword, '%')")
    List<Customer> searchByName(@Param("keyword") String keyword);

    // 5. Total balance aggregate query (JPQL)
    @Query("SELECT SUM(c.balance) FROM Customer c")
    BigDecimal getTotalBalance();

    // 6. Native SQL query to count customers
    @Query(value = "SELECT COUNT(*) FROM customers", nativeQuery = true)
    long countCustomersNative();

    // 7. Debit (Subtract money atomically if balance is sufficient)
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Customer c SET c.balance = c.balance - :amount WHERE c.accountNumber = :accountNumber AND c.balance >= :amount")
    int debit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);

    // 8. Credit (Add money to account)
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Customer c SET c.balance = c.balance + :amount WHERE c.accountNumber = :accountNumber")
    int credit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);

    // Find customer by account number (alias if needed by service)
    @Query("SELECT c FROM Customer c WHERE c.accountNumber = :accountNumber")
    Optional<Customer> getCustomer(@Param("accountNumber") String accountNumber);

    // Native or JPQL count customers
    @Query("SELECT COUNT(c) FROM Customer c")
    long countCustomers();
}