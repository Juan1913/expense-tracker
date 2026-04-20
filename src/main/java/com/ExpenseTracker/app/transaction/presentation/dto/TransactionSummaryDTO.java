package com.ExpenseTracker.app.transaction.presentation.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Aggregates across the entire filter set (not just the current page).
 * Returned by {@code GET /api/v1/transactions/summary}.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionSummaryDTO {
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netBalance;
    private long incomeCount;
    private long expenseCount;
    private long totalCount;
}
