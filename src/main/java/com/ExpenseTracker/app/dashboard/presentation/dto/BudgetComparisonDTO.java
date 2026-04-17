package com.ExpenseTracker.app.dashboard.presentation.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetComparisonDTO {

    private UUID categoryId;
    private String category;
    private BigDecimal budgeted;
    private BigDecimal actual;
    private BigDecimal percentage;
}
