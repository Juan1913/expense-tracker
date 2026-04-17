package com.ExpenseTracker.app.admin.service;

import com.ExpenseTracker.app.admin.presentation.dto.AdminCreateUserDTO;
import com.ExpenseTracker.app.user.presentation.dto.UserDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IAdminUserService {

    UserDTO inviteUser(AdminCreateUserDTO dto);

    Page<UserDTO> listUsers(Pageable pageable);

    UserDTO toggleActive(UUID userId);

    void deleteUser(UUID userId);
}
