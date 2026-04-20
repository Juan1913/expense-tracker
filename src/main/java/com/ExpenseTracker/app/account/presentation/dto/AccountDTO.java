package com.ExpenseTracker.app.account.presentation.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountDTO {
    private UUID id;
    private String name;
    private String bank;
    private String cardNumber;
    private String description;
    private BigDecimal balance;
    private String currency;
    private LocalDateTime createdAt;
}
