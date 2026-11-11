package com.example.bankapp.service;

import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.exception.InsufficientBalanceException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.transaction.annotation.Propagation;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // default for every method: read-only transaction
public class BankService {

    private final CustomerRepository customerRepository;
    private final BankTransactionRepository transactionRepository;

    // ---------- Reads ----------

    public List<Customer> getAllCustomers() {
        return customerRepository.findAllOrdered();
    }

    public List<Customer> searchCustomers(String keyword) {
        return customerRepository.searchByName(keyword);
    }

    public Customer getCustomer(String accountNumber) {
        return customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    public BigDecimal getTotalBalance() {
        BigDecimal total = customerRepository.getTotalBalance();
        return total == null ? BigDecimal.ZERO : total;   // SUM is null when there are no customers
    }

    public long countCustomers() {
        return customerRepository.countCustomersNative();
    }

    public List<BankTransaction> getLatestTransactions() {
        return transactionRepository.findAllLatest();
    }

    public List<BankTransaction> getHistory(String accountNumber) {
        return transactionRepository.findHistory(accountNumber);
    }

    public long countByStatus(TransactionStatus status) {
        return transactionRepository.countByStatus(status);
    }

    // Task 12: transfers above a given amount
    public List<BankTransaction> getLargeTransfers(BigDecimal min) {
        return transactionRepository.findLargeTransfers(min);
    }

    // Task 12: total amount sent by an account (SUCCESS transfers only)
    public BigDecimal getTotalSent(String accountNumber) {
        BigDecimal total = transactionRepository.findTotalSent(accountNumber, TransactionStatus.SUCCESS);
        return total == null ? BigDecimal.ZERO : total;   // SUM is null when there are no rows
    }

    // ---------- Create customer ----------

    @Transactional   // read-write transaction (overrides readOnly = true)
    public Customer createCustomer(Customer customer) {
        if (customerRepository.countAccounts(customer.getAccountNumber()) > 0) {
            throw new BankException("Account number " + customer.getAccountNumber() + " already exists");
        }
        if (customerRepository.countEmails(customer.getEmail()) > 0) {
            throw new BankException("Email " + customer.getEmail() + " already exists");
        }
        customer.setBalance(customer.getBalance().setScale(2, RoundingMode.HALF_UP));
        return customerRepository.save(customer);
    }

    // ---------- THE HEART OF THE LAB ----------

    // ---------- THE HEART OF THE LAB (with 1% fee) ----------

    public static final String FEE_ACCOUNT = "999999";
    private static final BigDecimal FEE_RATE = new BigDecimal("0.01");   // 1%

    public static BigDecimal calculateFee(BigDecimal amount) {
        return amount.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction transfer(String fromAccount, String toAccount,
                                    BigDecimal amount, boolean simulateFailure) {

        // Step 1: validate
        if (fromAccount.equals(toAccount)) {
            throw new BankException("Sender and receiver must be different accounts");
        }
        if (FEE_ACCOUNT.equals(fromAccount) || FEE_ACCOUNT.equals(toAccount)) {
            throw new BankException("The bank fee account cannot take part in transfers");
        }
        if (customerRepository.countAccounts(fromAccount) == 0) {
            throw new AccountNotFoundException(fromAccount);
        }
        if (customerRepository.countAccounts(toAccount) == 0) {
            throw new AccountNotFoundException(toAccount);
        }

        amount = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = calculateFee(amount);
        BigDecimal totalDebit = amount.add(fee);

        // Step 2: withdraw amount + fee from the sender (0 rows = balance too low)
        if (customerRepository.debit(fromAccount, totalDebit) == 0) {
            throw new InsufficientBalanceException(fromAccount);
        }

        // Step 3: simulated crash AFTER the money has left the sender
        if (simulateFailure) {
            throw new IllegalStateException("Simulated system failure after the withdrawal");
        }

        // Step 4: deposit the amount to the receiver
        customerRepository.credit(toAccount, amount);

        // Step 5: deposit the fee to the bank account (rolls everything back if it is missing)
        if (customerRepository.credit(FEE_ACCOUNT, fee) == 0) {
            throw new BankException("Bank fee account " + FEE_ACCOUNT + " does not exist");
        }

        // Step 6: save the SUCCESS record
        return transactionRepository.save(
                new BankTransaction(fromAccount, toAccount, amount,
                        TransactionStatus.SUCCESS, "Transfer completed (fee LKR " + fee + ")"));
    }

    // ---------- Task 12: Deposit / Withdraw ----------

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction deposit(String accountNumber, BigDecimal amount) {
        amount = checkAmount(amount);
        if (customerRepository.countAccounts(accountNumber) == 0) {
            throw new AccountNotFoundException(accountNumber);
        }
        customerRepository.credit(accountNumber, amount);
        // deposit has no sender, so fromAccount is null
        return transactionRepository.save(
                new BankTransaction(null, accountNumber, amount,
                        TransactionStatus.SUCCESS, "Deposit"));
    }

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction withdraw(String accountNumber, BigDecimal amount) {
        amount = checkAmount(amount);
        if (customerRepository.countAccounts(accountNumber) == 0) {
            throw new AccountNotFoundException(accountNumber);
        }
        if (customerRepository.debit(accountNumber, amount) == 0) {   // 0 rows = balance too low
            throw new InsufficientBalanceException(accountNumber);
        }
        // withdrawal has no receiver, so toAccount is null
        return transactionRepository.save(
                new BankTransaction(accountNumber, null, amount,
                        TransactionStatus.SUCCESS, "Withdrawal"));
    }

    private BigDecimal checkAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BankException("Amount must be greater than 0");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}