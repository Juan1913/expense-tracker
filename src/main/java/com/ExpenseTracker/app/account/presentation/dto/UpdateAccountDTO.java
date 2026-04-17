package com.ExpenseTracker.app.account.presentation.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateAccountDTO {

    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String name;

    @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
    private String description;

    private BigDecimal balance;

    @Size(max = 10, message = "La moneda no puede superar 10 caracteres")
    private String currency;
}
