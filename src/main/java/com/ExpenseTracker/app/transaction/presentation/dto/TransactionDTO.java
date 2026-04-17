package com.ExpenseTracker.app.transaction.presentation.dto;

import com.ExpenseTracker.util.enums.TransactionType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionDTO {
    private UUID id;
    private BigDecimal amount;
    private LocalDateTime date;
    private String description;
    private TransactionType type;
    private UUID accountId;
    private String accountName;
    private UUID categoryId;
    private String categoryName;
    private UUID userId;
    private LocalDateTime createdAt;
}
