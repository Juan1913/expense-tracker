package com.ExpenseTracker.app.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordDTO {

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Correo inválido")
    private String email;
}
