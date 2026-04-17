package com.ExpenseTracker.app.wishlist.presentation.dto;

import com.ExpenseTracker.util.enums.WishlistStatus;
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
public class UpdateWishlistDTO {

    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String name;

    @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
    private String description;

    @DecimalMin(value = "0.01", message = "El monto objetivo debe ser mayor a 0")
    private BigDecimal targetAmount;

    @DecimalMin(value = "0.00", inclusive = true, message = "El monto actual no puede ser negativo")
    private BigDecimal currentAmount;

    private LocalDate deadline;

    private WishlistStatus status;
}
