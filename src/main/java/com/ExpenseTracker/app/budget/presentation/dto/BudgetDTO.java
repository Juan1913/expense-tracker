package com.ExpenseTracker.app.budget.presentation.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetDTO {

    private UUID id;
    private BigDecimal amount;
    private int month;
    private int year;
    private UUID categoryId;
    private String categoryName;
    private UUID userId;
    private LocalDateTime createdAt;
}
