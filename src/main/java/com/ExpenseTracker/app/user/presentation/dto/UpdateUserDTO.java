package com.ExpenseTracker.app.user.presentation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserDTO {

    @Size(min = 3, max = 50, message = "El username debe tener entre 3 y 50 caracteres")
    private String username;

    @Email(message = "El email no tiene formato válido")
    private String email;

    @Size(min = 6, message = "La contraseña debe tener mínimo 6 caracteres")
    private String password;

    @DecimalMin(value = "0.00", inclusive = true, message = "La meta de ahorro no puede ser negativa")
    private BigDecimal monthlySavingsGoal;
}
