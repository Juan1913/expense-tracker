package com.ExpenseTracker.app.transaction.presentation.dto;

import com.ExpenseTracker.util.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTransactionDTO {

    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal amount;

    private LocalDateTime date;

    @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
    private String description;

    private TransactionType type;

    private UUID accountId;

    private UUID transferToAccountId;

    private UUID categoryId;
}
