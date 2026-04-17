package com.ExpenseTracker.app.user.service;

import com.ExpenseTracker.app.user.mapper.UserMapper;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.app.user.presentation.dto.CreateUserDTO;
import com.ExpenseTracker.app.user.presentation.dto.UpdateUserDTO;
import com.ExpenseTracker.app.user.presentation.dto.UserDTO;
import com.ExpenseTracker.util.exception.AlreadyExistsException;
import com.ExpenseTracker.util.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserEntityRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UserServiceImpl service;

    // ── createUser ──────────────────────────────────────────────────────────────

    @Test
    void createUser_whenEmailAlreadyExists_throwsAlreadyExistsException() {
        CreateUserDTO dto = createUserDTO("taken@mail.com", "pass123");
        when(userRepository.existsByEmail("taken@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(dto))
                .isInstanceOf(AlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_whenEmailFree_encodesPasswordAndSaves() {
        UUID id = UUID.randomUUID();
        CreateUserDTO dto = createUserDTO("new@mail.com", "rawpass");

        UserEntity entity = UserEntity.builder().id(id).email("new@mail.com").build();
        UserDTO expected = UserDTO.builder().id(id).email("new@mail.com").build();

        when(userRepository.existsByEmail("new@mail.com")).thenReturn(false);
        when(userMapper.toEntity(dto)).thenReturn(entity);
        when(passwordEncoder.encode("rawpass")).thenReturn("hashed");
        when(userRepository.save(entity)).thenReturn(entity);
        when(userMapper.toDTO(entity)).thenReturn(expected);

        UserDTO result = service.createUser(dto);

        assertThat(entity.getPassword()).isEqualTo("hashed");
        assertThat(result).isEqualTo(expected);
    }

    // ── findById ────────────────────────────────────────────────────────────────

    @Test
    void findById_whenExists_returnsDTO() {
        UUID id = UUID.randomUUID();
        UserEntity entity = UserEntity.builder().id(id).build();
        UserDTO expected = UserDTO.builder().id(id).build();

        when(userRepository.findById(id)).thenReturn(Optional.of(entity));
        when(userMapper.toDTO(entity)).thenReturn(expected);

        assertThat(service.findById(id)).isEqualTo(expected);
    }

    @Test
    void findById_whenNotFound_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(NotFoundException.class);
    }

    // ── update ──────────────────────────────────────────────────────────────────

    @Test
    void update_whenUserNotFound_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new UpdateUserDTO()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_whenEmailTakenByOther_throwsAlreadyExistsException() {
        UUID id = UUID.randomUUID();
        UserEntity entity = UserEntity.builder().id(id).email("old@mail.com").build();
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setEmail("taken@mail.com");

        when(userRepository.findById(id)).thenReturn(Optional.of(entity));
        when(userRepository.existsByEmail("taken@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, dto))
                .isInstanceOf(AlreadyExistsException.class);
    }

    @Test
    void update_withNewPassword_encodesIt() {
        UUID id = UUID.randomUUID();
        UserEntity entity = UserEntity.builder().id(id).email("u@mail.com").build();
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setPassword("newpass");

        when(userRepository.findById(id)).thenReturn(Optional.of(entity));
        when(passwordEncoder.encode("newpass")).thenReturn("newhash");
        when(userRepository.save(entity)).thenReturn(entity);
        when(userMapper.toDTO(entity)).thenReturn(UserDTO.builder().id(id).build());

        service.update(id, dto);

        assertThat(entity.getPassword()).isEqualTo("newhash");
        verify(passwordEncoder).encode("newpass");
    }

    // ── delete ──────────────────────────────────────────────────────────────────

    @Test
    void delete_whenNotFound_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(NotFoundException.class);

        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void delete_whenExists_callsDeleteById() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(true);

        service.delete(id);

        verify(userRepository).deleteById(id);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private CreateUserDTO createUserDTO(String email, String password) {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setEmail(email);
        dto.setPassword(password);
        dto.setUsername("testuser");
        return dto;
    }
}
