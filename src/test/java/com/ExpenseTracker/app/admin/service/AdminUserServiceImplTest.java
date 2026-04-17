package com.ExpenseTracker.app.admin.service;

import com.ExpenseTracker.app.admin.presentation.dto.AdminCreateUserDTO;
import com.ExpenseTracker.app.user.mapper.UserMapper;
import com.ExpenseTracker.app.user.persistence.entity.EmailVerificationToken;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.EmailVerificationTokenRepository;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.app.user.presentation.dto.UserDTO;
import com.ExpenseTracker.infrastructure.email.EmailService;
import com.ExpenseTracker.util.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock private UserEntityRepository userRepository;
    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private EmailService emailService;
    @Mock private UserMapper userMapper;

    @InjectMocks private AdminUserServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:5173");
    }

    // ── inviteUser ──────────────────────────────────────────────────────────────

    @Test
    void inviteUser_whenEmailAlreadyExists_throwsIllegalArgumentException() {
        AdminCreateUserDTO dto = dto("existing@test.com");
        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> service.inviteUser(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe un usuario");

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
    }

    @Test
    void inviteUser_whenEmailNew_createsInactiveUserSendsEmailReturnsDTO() {
        AdminCreateUserDTO dto = dto("new@test.com");

        UserEntity savedUser = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("new@test.com")
                .role("USER")
                .active(false)
                .emailVerified(false)
                .build();

        UserDTO expectedDTO = UserDTO.builder().id(savedUser.getId()).email(savedUser.getEmail()).build();

        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDTO(savedUser)).thenReturn(expectedDTO);

        UserDTO result = service.inviteUser(dto);

        assertThat(result).isEqualTo(expectedDTO);

        // user saved with correct state
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        UserEntity captured = userCaptor.getValue();
        assertThat(captured.getEmail()).isEqualTo("new@test.com");
        assertThat(captured.getRole()).isEqualTo("USER");
        assertThat(captured.isActive()).isFalse();
        assertThat(captured.isEmailVerified()).isFalse();
        assertThat(captured.getPassword()).isNull();

        // token saved with 24h expiry and correct user reference
        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        EmailVerificationToken capturedToken = tokenCaptor.getValue();
        assertThat(capturedToken.getToken()).isNotBlank();
        assertThat(capturedToken.getUser()).isEqualTo(savedUser);
        assertThat(capturedToken.isUsed()).isFalse();
        assertThat(capturedToken.getExpiresAt()).isAfter(java.time.LocalDateTime.now().plusHours(23));

        // email sent with correct address and frontend URL
        verify(emailService).sendVerificationEmail(
                eq("new@test.com"),
                eq(capturedToken.getToken()),
                eq("http://localhost:5173")
        );
    }

    // ── listUsers ───────────────────────────────────────────────────────────────

    @Test
    void listUsers_returnsPageOfMappedDTOs() {
        UserEntity user = UserEntity.builder().id(UUID.randomUUID()).email("a@b.com").build();
        UserDTO userDTO = UserDTO.builder().id(user.getId()).email(user.getEmail()).build();

        PageRequest pageable = PageRequest.of(0, 10);
        Page<UserEntity> entityPage = new PageImpl<>(List.of(user), pageable, 1);

        when(userRepository.findAll(pageable)).thenReturn(entityPage);
        when(userMapper.toDTO(user)).thenReturn(userDTO);

        Page<UserDTO> result = service.listUsers(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0)).isEqualTo(userDTO);
    }

    // ── toggleActive ────────────────────────────────────────────────────────────

    @Test
    void toggleActive_whenUserIsActive_setsInactive() {
        UUID id = UUID.randomUUID();
        UserEntity user = UserEntity.builder().id(id).email("u@test.com").active(true).build();
        UserDTO dto = UserDTO.builder().id(id).active(false).build();

        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDTO(user)).thenReturn(dto);

        UserDTO result = service.toggleActive(id);

        assertThat(user.isActive()).isFalse();
        assertThat(result).isEqualTo(dto);
        verify(userRepository).save(user);
    }

    @Test
    void toggleActive_whenUserIsInactive_setsActive() {
        UUID id = UUID.randomUUID();
        UserEntity user = UserEntity.builder().id(id).email("u@test.com").active(false).build();

        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDTO(user)).thenReturn(UserDTO.builder().id(id).active(true).build());

        service.toggleActive(id);

        assertThat(user.isActive()).isTrue();
    }

    @Test
    void toggleActive_whenUserNotFound_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleActive(id))
                .isInstanceOf(NotFoundException.class);
    }

    // ── deleteUser ──────────────────────────────────────────────────────────────

    @Test
    void deleteUser_whenUserNotFound_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteUser(id))
                .isInstanceOf(NotFoundException.class);

        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void deleteUser_whenUserExists_callsDeleteById() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(true);

        service.deleteUser(id);

        verify(userRepository).deleteById(id);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private AdminCreateUserDTO dto(String email) {
        AdminCreateUserDTO dto = new AdminCreateUserDTO();
        dto.setEmail(email);
        return dto;
    }
}
