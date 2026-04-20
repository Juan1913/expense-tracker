package com.ExpenseTracker.app.account.presentation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateAccountDTO {

    @NotBlank(message = "El nombre de la cuenta es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String name;

    @NotBlank(message = "El banco es obligatorio")
    @Size(max = 60, message = "El banco no puede superar 60 caracteres")
    private String bank;

    @Size(max = 40, message = "El número de tarjeta no puede superar 40 caracteres")
    private String cardNumber;

    @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
    private String description;

    @DecimalMin(value = "0.0", message = "El balance no puede ser negativo")
    private BigDecimal balance;

    @Size(max = 10, message = "La moneda no puede superar 10 caracteres")
    private String currency;
}
