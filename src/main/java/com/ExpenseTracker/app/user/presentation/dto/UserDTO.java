package com.ExpenseTracker.app.user.presentation.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
    private UUID id;
    private String username;
    private String email;
    private String role;
    private BigDecimal monthlySavingsGoal;
    private boolean active;
    private boolean emailVerified;
    private LocalDateTime createdAt;
}
