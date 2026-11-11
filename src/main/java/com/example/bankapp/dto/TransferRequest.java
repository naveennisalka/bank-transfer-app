package com.example.bankapp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Holds the data typed into the "Transfer Money" form. */
@Getter @Setter
public class TransferRequest {

    @NotBlank(message = "Choose the sender account")
    private String fromAccount;

    @NotBlank(message = "Choose the receiver account")
    private String toAccount;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 12, fraction = 2, message = "Max 2 decimal places")
    private BigDecimal amount;

    private boolean simulateFailure;
}
