package com.example.bankapp.controller;

import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import org.springframework.http.MediaType;

class BankControllerJsonTransferTest {

    private BankService bankService;
    private TransactionAuditService auditService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        bankService = mock(BankService.class);
        auditService = mock(TransactionAuditService.class);
        mockMvc = standaloneSetup(new BankController(bankService, auditService)).build();
    }

    @Test
    void acceptsJsonTransferAndReturnsReceipt() throws Exception {
        when(bankService.transfer("100001", "100002", new BigDecimal("5000.00"), false))
                .thenReturn(new BankTransaction("100001", "100002", new BigDecimal("5000.00"),
                        TransactionStatus.SUCCESS, "Transfer completed (fee LKR 50.00)"));

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromAccount": "100001",
                                  "toAccount": "100002",
                                  "amount": 5000.00,
                                  "simulateFailure": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromAccount").value("100001"))
                .andExpect(jsonPath("$.toAccount").value("100002"))
                .andExpect(jsonPath("$.amount").value(5000.0))
                .andExpect(jsonPath("$.fee").value(50.0))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(bankService).transfer("100001", "100002", new BigDecimal("5000.00"), false);
    }

    @Test
    void returnsJsonErrorWhenTransferCannotBeCompleted() throws Exception {
        when(bankService.transfer(eq("100001"), eq("999000"),
                eq(new BigDecimal("5000.00")), eq(false)))
                .thenThrow(new AccountNotFoundException("999000"));

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromAccount": "100001",
                                  "toAccount": "999000",
                                  "amount": 5000.00,
                                  "simulateFailure": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Account 999000 was not found"));

        verify(auditService).recordFailure("100001", "999000",
                new BigDecimal("5000.00"), "Account 999000 was not found");
    }

    @Test
    void keepsFormTransferRedirectBehavior() throws Exception {
        when(bankService.transfer("100001", "100002", new BigDecimal("5000.00"), false))
                .thenReturn(new BankTransaction("100001", "100002", new BigDecimal("5000.00"),
                        TransactionStatus.SUCCESS, "Transfer completed (fee LKR 50.00)"));

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fromAccount", "100001")
                        .param("toAccount", "100002")
                        .param("amount", "5000.00")
                        .param("simulateFailure", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(bankService).transfer("100001", "100002", new BigDecimal("5000.00"), false);
    }
}
