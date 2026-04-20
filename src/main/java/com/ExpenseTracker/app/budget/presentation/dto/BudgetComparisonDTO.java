package com.ExpenseTracker.app.budget.presentation.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Budget + actual spent for a given period. Used by the presupuestos page
 * to render progress vs budgeted without needing two round-trips.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BudgetComparisonDTO {
    private UUID id;
    private BigDecimal budgeted;
    private BigDecimal actual;
    private BigDecimal percentage;
    private int month;
    private int year;
    private UUID categoryId;
    private String categoryName;
    private LocalDateTime createdAt;
}
