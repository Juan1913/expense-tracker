package com.ExpenseTracker.app.debt.presentation.dto;

import com.ExpenseTracker.util.enums.DebtStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDebtDTO {

    @Size(max = 100) private String name;
    @Size(max = 255) private String description;
    @Size(max = 100) private String creditor;

    @DecimalMin(value = "0.01") private BigDecimal principal;

    @DecimalMin(value = "0.00", inclusive = true) private BigDecimal currentBalance;

    @DecimalMin(value = "0.00", inclusive = true)
    @DecimalMax(value = "5.00")
    private BigDecimal annualRate;

    @DecimalMin(value = "0.00", inclusive = true) private BigDecimal minimumPayment;

    private LocalDate startDate;
    private DebtStatus status;
}
