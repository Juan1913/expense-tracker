package com.ExpenseTracker.app.debt.presentation.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtPaymentDTO {
    private UUID id;
    private UUID debtId;
    private BigDecimal amountTotal;
    private BigDecimal amountInterest;
    private BigDecimal amountCapital;
    private BigDecimal balanceAfter;
    private LocalDate paymentDate;
    private UUID accountId;
    private String accountName;
    private UUID transactionId;
    private LocalDateTime createdAt;
}
