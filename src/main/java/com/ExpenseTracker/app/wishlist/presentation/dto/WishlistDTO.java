package com.ExpenseTracker.app.wishlist.presentation.dto;

import com.ExpenseTracker.util.enums.WishlistStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistDTO {

    private UUID id;
    private String name;
    private String description;
    private BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private BigDecimal progressPercentage;
    private LocalDate deadline;
    private WishlistStatus status;
    private LocalDateTime createdAt;
    private UUID userId;
}
