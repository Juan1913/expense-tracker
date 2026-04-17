package com.ExpenseTracker.app.dashboard.presentation.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlySummaryDTO {

    private int year;
    private int month;
    private String monthName;
    private BigDecimal income;
    private BigDecimal expenses;
    private BigDecimal savings;
}
