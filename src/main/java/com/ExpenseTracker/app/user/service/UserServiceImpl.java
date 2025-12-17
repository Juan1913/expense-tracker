package com.ExpenseTracker.app.user.service;

import com.ExpenseTracker.app.user.presentation.dto.CreateUserDTO;
import com.ExpenseTracker.app.user.presentation.dto.UserDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public class UserServiceImpl implements IUserService{
    @Override
    public CreateUserDTO createUser(CreateUserDTO createUserDTO) {
        return null;
    }

    @Override
    public Page<UserDTO> findAll(Pageable pageable) {
        return null;
    }

    @Override
    public UserDTO findById(UUID id) {
        return null;
    }

    @Override
    public UserDTO update(UUID id, UserDTO userDTO) {
        return null;
    }

    @Override
    public void delete(UUID id) {

    }
}
