package com.ExpenseTracker.app.tag.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateTagDTO {

    @NotBlank(message = "La descripción de la etiqueta es obligatoria")
    @Size(max = 100, message = "La descripción no puede superar 100 caracteres")
    private String description;
}
