package com.ExpenseTracker.app.auth.presentation.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {

    private UUID userId;
    private String email;
    private String username;
    private String role;
}
