package com.ExpenseTracker.app.debt.presentation.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDebtDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String name;

    @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
    private String description;

    @Size(max = 100, message = "El acreedor no puede superar 100 caracteres")
    private String creditor;

    @NotNull(message = "El monto principal es obligatorio")
    @DecimalMin(value = "0.01", message = "El principal debe ser mayor a 0")
    private BigDecimal principal;

    /** Saldo actual (opcional — si se omite, se asume = principal). */
    @DecimalMin(value = "0.00", inclusive = true, message = "El saldo no puede ser negativo")
    private BigDecimal currentBalance;

    @NotNull(message = "La tasa anual es obligatoria")
    @DecimalMin(value = "0.00", inclusive = true, message = "La tasa no puede ser negativa")
    @DecimalMax(value = "5.00", message = "La tasa anual parece excesiva (>500%)")
    private BigDecimal annualRate;

    @NotNull(message = "El pago mínimo es obligatorio")
    @DecimalMin(value = "0.00", inclusive = true, message = "El pago mínimo no puede ser negativo")
    private BigDecimal minimumPayment;

    private LocalDate startDate;
}
