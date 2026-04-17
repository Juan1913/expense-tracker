package com.ExpenseTracker.app.user.service;

import com.ExpenseTracker.app.user.mapper.UserMapper;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.app.user.presentation.dto.CreateUserDTO;
import com.ExpenseTracker.app.user.presentation.dto.UpdateUserDTO;
import com.ExpenseTracker.app.user.presentation.dto.UserDTO;
import com.ExpenseTracker.util.exception.AlreadyExistsException;
import com.ExpenseTracker.util.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements IUserService {

    private final UserEntityRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDTO createUser(CreateUserDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new AlreadyExistsException("El correo ya está registrado: " + dto.getEmail());
        }
        UserEntity entity = userMapper.toEntity(dto);
        entity.setPassword(passwordEncoder.encode(dto.getPassword()));
        return userMapper.toDTO(userRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserDTO> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO findById(UUID id) {
        return userRepository.findById(id)
                .map(userMapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado con id: " + id));
    }

    @Override
    public UserDTO update(UUID id, UpdateUserDTO dto) {
        UserEntity entity = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado con id: " + id));

        if (dto.getEmail() != null && !dto.getEmail().equals(entity.getEmail())
                && userRepository.existsByEmail(dto.getEmail())) {
            throw new AlreadyExistsException("El correo ya está registrado: " + dto.getEmail());
        }

        userMapper.updateEntityFromDTO(dto, entity);

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            entity.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        return userMapper.toDTO(userRepository.save(entity));
    }

    @Override
    public void delete(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("Usuario no encontrado con id: " + id);
        }
        userRepository.deleteById(id);
    }
}
