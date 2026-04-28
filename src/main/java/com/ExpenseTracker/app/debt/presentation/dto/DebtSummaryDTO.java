package com.ExpenseTracker.app.debt.presentation.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtSummaryDTO {
    private UUID debtId;
    private BigDecimal totalCapitalPaid;
    private BigDecimal totalInterestPaid;
    private BigDecimal currentBalance;
    private BigDecimal nextMonthInterestEstimate;
    private BigDecimal capitalProgressPercentage;
    private int paymentsCount;
    /** "GOOD" | "MEDIUM" | "BAD" según tasa anual. */
    private String qualityBadge;
    /** Mensaje corto explicando la clasificación. */
    private String qualityHint;
}
