package com.ExpenseTracker.app.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetupProfileDTO {

    @NotBlank(message = "El token de configuración es requerido")
    private String setupToken;

    @NotBlank(message = "El nombre de usuario es requerido")
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank(message = "La contraseña es requerida")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;

    private String profileImageUrl;
}
