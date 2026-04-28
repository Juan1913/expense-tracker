package com.ExpenseTracker.app.dashboard.presentation.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDTO {

    private long totalTransactions;
    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    /** Σ(income) − Σ(expense) histórico, ignora TRANSFER. */
    private BigDecimal totalSavings;
    private BigDecimal totalNetWorth;
    private BigDecimal totalInSavingsAccounts;
    private BigDecimal totalOperational;
    private BigDecimal totalCreditCardDebt;
    private List<MonthlySummaryDTO> monthlySummaries;
    private List<CategoryExpenseDTO> expensesByCategory;
    private List<BudgetComparisonDTO> budgetComparisons;
    private List<MonthlySavingsProgressDTO> monthlySavingsProgress;
}
