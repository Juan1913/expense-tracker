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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserServiceImpl implements IAdminUserService {

    private final UserEntityRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final UserMapper userMapper;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    @Transactional
    public UserDTO inviteUser(AdminCreateUserDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario con ese correo");
        }

        UserEntity user = UserEntity.builder()
                .email(dto.getEmail())
                .role("USER")
                .active(false)
                .emailVerified(false)
                .build();
        user = userRepository.save(user);

        String rawToken = UUID.randomUUID().toString();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .token(rawToken)
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        tokenRepository.save(verificationToken);

        emailService.sendVerificationEmail(user.getEmail(), rawToken, frontendUrl);
        log.info("Invitación enviada a {}", user.getEmail());

        return userMapper.toDTO(user);
    }

    @Override
    public Page<UserDTO> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toDTO);
    }

    @Override
    @Transactional
    public UserDTO toggleActive(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        user.setActive(!user.isActive());
        return userMapper.toDTO(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Usuario no encontrado");
        }
        userRepository.deleteById(userId);
    }
}
