package com.ExpenseTracker.app.dashboard.presentation.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlySavingsProgressDTO {

    private int year;
    private int month;
    private String monthName;
    private BigDecimal savingsAmount;
    private BigDecimal savingsGoal;
    private BigDecimal progressPercentage;
}
