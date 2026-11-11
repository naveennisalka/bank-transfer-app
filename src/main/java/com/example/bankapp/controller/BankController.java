package com.example.bankapp.controller;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class BankController {
    private final BankService bankService;
    private final TransactionAuditService auditService;

    // ---------- Dashboard ----------
    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) String keyword, Model model) {
        loadDashboard(model, keyword);
        return "bank";
    }

    private void loadDashboard(Model model, String keyword) {
        boolean search = keyword != null && !keyword.isBlank();
        List<Customer> customers = search
                ? bankService.searchCustomers(keyword.trim())
                : bankService.getAllCustomers();

        model.addAttribute("customers", customers);
        model.addAttribute("allCustomers", bankService.getAllCustomers());
        model.addAttribute("keyword", keyword);
        model.addAttribute("totalCustomers", bankService.countCustomers());
        model.addAttribute("totalBalance", bankService.getTotalBalance());
        model.addAttribute("successCount", bankService.countByStatus(TransactionStatus.SUCCESS));
        model.addAttribute("failedCount", bankService.countByStatus(TransactionStatus.FAILED));
        model.addAttribute("transactions",
                bankService.getLatestTransactions().stream().limit(20).toList());

        // form objects (only if not already there, e.g. after a validation error)
        if (!model.containsAttribute("transferRequest")) {
            model.addAttribute("transferRequest", new TransferRequest());
        }
        if (!model.containsAttribute("customer")) {
            model.addAttribute("customer", new Customer());
        }
    }

    // ---------- Add customer ----------
    @PostMapping("/customers")
    public String addCustomer(@Valid @ModelAttribute("customer") Customer customer,
                              BindingResult result, Model model,
                              RedirectAttributes redirect) {
        if (result.hasErrors()) {
            loadDashboard(model, null);
            return "bank";
        }
        try {
            bankService.createCustomer(customer);
            redirect.addFlashAttribute("success", "Customer " + customer.getFullName() + " added");
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    // ---------- Transfer ----------
    @PostMapping("/transfer")
    public String transfer(@Valid @ModelAttribute("transferRequest") TransferRequest request,
                           BindingResult result, Model model,
                           RedirectAttributes redirect) {
        if (result.hasErrors()) {
            loadDashboard(model, null);
            return "bank";
        }
        try {
            bankService.transfer(request.getFromAccount(), request.getToAccount(),
                                 request.getAmount(), request.isSimulateFailure());
            redirect.addFlashAttribute("success", "Transfer of LKR " + request.getAmount()
                    + " completed (fee LKR " + BankService.calculateFee(request.getAmount()) + ")");
        } catch (Exception e) {
            String reason = (e instanceof BankException)
                    ? e.getMessage()
                    : e.getMessage() + ". All changes were rolled back.";
            auditService.recordFailure(request.getFromAccount(), request.getToAccount(),
                                       request.getAmount(), reason);
            redirect.addFlashAttribute("error", "Transfer failed: " + reason);
        }
        return "redirect:/";
    }

    @PostMapping(value = "/transfer", consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> transferJson(@Valid @RequestBody TransferRequest request) {
        try {
            BankTransaction transaction = bankService.transfer(
                    request.getFromAccount(), request.getToAccount(),
                    request.getAmount(), request.isSimulateFailure());
            return ResponseEntity.ok(new TransferResponse(
                    transaction.getId(),
                    transaction.getFromAccount(),
                    transaction.getToAccount(),
                    transaction.getAmount(),
                    BankService.calculateFee(transaction.getAmount()),
                    transaction.getStatus().name(),
                    transaction.getDescription()));
        } catch (BankException e) {
            auditService.recordFailure(request.getFromAccount(), request.getToAccount(),
                    request.getAmount(), e.getMessage());
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        } catch (RuntimeException e) {
            String reason = e.getMessage() == null ? "Unexpected system error" : e.getMessage();
            auditService.recordFailure(request.getFromAccount(), request.getToAccount(),
                    request.getAmount(), reason);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("Transfer could not be completed due to a system error."));
        }
    }

    public record TransferResponse(Long transactionId, String fromAccount, String toAccount,
                                   BigDecimal amount, BigDecimal fee, String status, String message) {
    }

    public record ApiError(String error) {
    }

    // ---------- History ----------
    @GetMapping("/accounts/{accountNumber}/history")
    public String history(@PathVariable String accountNumber, Model model,
                          RedirectAttributes redirect) {
        try {
            model.addAttribute("customer", bankService.getCustomer(accountNumber));
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
        model.addAttribute("transactions", bankService.getHistory(accountNumber));
        model.addAttribute("totalSent", bankService.getTotalSent(accountNumber));
        return "history";
    }

    // ---------- Task 12: large transfers ----------
    @GetMapping("/large-transfers")
    public String largeTransfers(@RequestParam(defaultValue = "10000") BigDecimal min, Model model) {
        model.addAttribute("min", min);
        model.addAttribute("transfers", bankService.getLargeTransfers(min));
        return "large-transfers";
    }

    // ---------- Task 12: Deposit ----------
    @GetMapping("/deposit")
    public String depositForm(Model model) {
        model.addAttribute("customers", bankService.getAllCustomers());
        return "deposit";
    }

    @PostMapping("/deposit")
    public String deposit(@RequestParam String accountNumber,
                          @RequestParam(required = false) BigDecimal amount,
                          RedirectAttributes redirect) {
        try {
            bankService.deposit(accountNumber, amount);
            redirect.addFlashAttribute("success",
                    "Deposited LKR " + amount + " into account " + accountNumber);
        } catch (Exception e) {
            recordFailure(null, accountNumber, amount, e.getMessage());
            redirect.addFlashAttribute("error", "Deposit failed: " + e.getMessage());
        }
        return "redirect:/deposit";
    }

    // ---------- Task 12: Withdraw ----------
    @GetMapping("/withdraw")
    public String withdrawForm(Model model) {
        model.addAttribute("customers", bankService.getAllCustomers());
        return "withdraw";
    }

    @PostMapping("/withdraw")
    public String withdraw(@RequestParam String accountNumber,
                           @RequestParam(required = false) BigDecimal amount,
                           RedirectAttributes redirect) {
        try {
            bankService.withdraw(accountNumber, amount);
            redirect.addFlashAttribute("success",
                    "Withdrew LKR " + amount + " from account " + accountNumber);
        } catch (Exception e) {
            recordFailure(accountNumber, null, amount, e.getMessage());
            redirect.addFlashAttribute("error", "Withdrawal failed: " + e.getMessage());
        }
        return "redirect:/withdraw";
    }

    // Saves a FAILED row (separate REQUIRES_NEW transaction) when the amount is valid
    private void recordFailure(String from, String to, BigDecimal amount, String reason) {
        if (amount != null && amount.signum() > 0) {
            auditService.recordFailure(from, to, amount, reason);
        }
    }
}