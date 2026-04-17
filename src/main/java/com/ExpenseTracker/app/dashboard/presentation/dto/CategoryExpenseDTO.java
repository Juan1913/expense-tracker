package com.ExpenseTracker.app.dashboard.presentation.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryExpenseDTO {

    private String category;
    private BigDecimal amount;
    private BigDecimal percentage;
}
