package com.ExpenseTracker.app.user.presentation.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@Builder

public class UserDTO {
    private UUID id;
    private String username;
    private String password;
    private String email;
    private String role;
    private String createdAt;
}
