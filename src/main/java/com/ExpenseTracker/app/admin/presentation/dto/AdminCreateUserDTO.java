package com.ExpenseTracker.app.admin.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminCreateUserDTO {

    @NotBlank(message = "El correo es requerido")
    @Email(message = "Debe ser un correo válido")
    private String email;
}
