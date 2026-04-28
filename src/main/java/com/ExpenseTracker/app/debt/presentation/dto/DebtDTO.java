package com.ExpenseTracker.app.debt.presentation.dto;

import com.ExpenseTracker.util.enums.DebtStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtDTO {

    private UUID id;
    private String name;
    private String description;
    private String creditor;
    private BigDecimal principal;
    private BigDecimal currentBalance;
    private BigDecimal annualRate;
    private BigDecimal monthlyRate;          // calculado: (1 + r)^(1/12) − 1
    private BigDecimal minimumPayment;
    private BigDecimal progressPercentage;   // 1 − currentBalance / principal
    private LocalDate startDate;
    private DebtStatus status;
    private LocalDateTime createdAt;
    private UUID userId;
    private String qualityBadge;
    private String qualityHint;
}
