package com.ExpenseTracker.app.user.presentation.dto;

import lombok.*;


@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateUserDTO {

    private String password;
    private String email;
    private String role;

}
