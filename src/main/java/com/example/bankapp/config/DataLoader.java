package com.example.bankapp.config;

import com.example.bankapp.model.Customer;
import com.example.bankapp.repository.CustomerRepository;
import com.example.bankapp.service.BankService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Inserts sample customers (first run only) and makes sure the bank fee account exists. */
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final CustomerRepository customerRepository;

    @Override
    public void run(String... args) {
        if (customerRepository.count() == 0) {
            customerRepository.saveAll(List.of(
                    new Customer("Kamal Gamage", "kamal@example.com", "100001", new BigDecimal("50000.00")),
                    new Customer("Nimali Perera", "nimali@example.com", "100002", new BigDecimal("25000.00")),
                    new Customer("Saman Kumara", "saman@example.com", "100003", new BigDecimal("10000.00"))
            ));
        }
        // Task 12: the account that receives the 1% transfer fees
        if (customerRepository.countAccounts(BankService.FEE_ACCOUNT) == 0) {
            customerRepository.save(new Customer("Bank Fee Account", "fees@bank.local",
                    BankService.FEE_ACCOUNT, new BigDecimal("0.00")));
        }
    }
}