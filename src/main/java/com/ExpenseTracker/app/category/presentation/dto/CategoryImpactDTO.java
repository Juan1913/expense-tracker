package com.ExpenseTracker.app.category.presentation.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CategoryImpactDTO {
    private long transactions;
    private long budgets;
}
