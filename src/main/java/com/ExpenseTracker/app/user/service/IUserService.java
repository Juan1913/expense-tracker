package com.ExpenseTracker.app.user.service;

import com.ExpenseTracker.app.user.presentation.dto.CreateUserDTO;
import com.ExpenseTracker.app.user.presentation.dto.UpdateUserDTO;
import com.ExpenseTracker.app.user.presentation.dto.UserDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IUserService {

    UserDTO createUser(CreateUserDTO dto);

    Page<UserDTO> findAll(Pageable pageable);

    UserDTO findById(UUID id);

    UserDTO update(UUID id, UpdateUserDTO dto);

    void delete(UUID id);
}
