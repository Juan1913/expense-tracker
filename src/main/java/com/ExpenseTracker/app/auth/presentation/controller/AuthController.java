package com.ExpenseTracker.app.auth.presentation.controller;

import com.ExpenseTracker.app.auth.presentation.dto.LoginRequestDTO;
import com.ExpenseTracker.app.auth.presentation.dto.LoginResponseDTO;
import com.ExpenseTracker.app.auth.presentation.dto.SetupProfileDTO;
import com.ExpenseTracker.app.user.persistence.entity.EmailVerificationToken;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.EmailVerificationTokenRepository;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.app.user.presentation.dto.CreateUserDTO;
import com.ExpenseTracker.app.user.presentation.dto.UserDTO;
import com.ExpenseTracker.app.user.service.IUserService;
import com.ExpenseTracker.infrastructure.security.JwtService;
import com.ExpenseTracker.util.exception.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registro e inicio de sesión")
public class AuthController {

    private final IUserService userService;
    private final UserEntityRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/register")
    @Operation(summary = "Registrar nuevo usuario")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody CreateUserDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(dto));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        UserEntity user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new NotFoundException("Credenciales inválidas"));

        if (!user.isActive()) {
            throw new DisabledException("Cuenta desactivada");
        }

        if (user.getPassword() == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());

        return ResponseEntity.ok(LoginResponseDTO.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(user.getRole())
                .profileImageUrl(user.getProfileImageUrl())
                .build());
    }

    @GetMapping("/verify")
    @Operation(summary = "Verificar token de invitación por email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        EmailVerificationToken verificationToken = tokenRepository.findByTokenAndUsedFalse(token)
                .orElseThrow(() -> new NotFoundException("Token inválido o ya utilizado"));

        if (verificationToken.isExpired()) {
            throw new BadCredentialsException("El enlace de verificación ha expirado");
        }

        UserEntity user = verificationToken.getUser();
        String setupToken = jwtService.generateSetupToken(user.getId());

        return ResponseEntity.ok(Map.of(
                "email", user.getEmail(),
                "setupToken", setupToken
        ));
    }

    @PostMapping("/setup-profile")
    @Operation(summary = "Configurar perfil tras verificar email")
    public ResponseEntity<LoginResponseDTO> setupProfile(@Valid @RequestBody SetupProfileDTO dto) {
        if (jwtService.isTokenExpired(dto.getSetupToken())) {
            throw new BadCredentialsException("El token de configuración ha expirado");
        }

        if (!jwtService.isSetupToken(dto.getSetupToken())) {
            throw new BadCredentialsException("Token inválido");
        }

        UUID userId = UUID.fromString(jwtService.extractUserId(dto.getSetupToken()));
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        // Mark verification token as used
        tokenRepository.findByTokenAndUsedFalse(dto.getSetupToken()).ifPresent(t -> {
            t.setUsed(true);
            tokenRepository.save(t);
        });

        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        if (dto.getProfileImageUrl() != null && !dto.getProfileImageUrl().isBlank()) {
            user.setProfileImageUrl(dto.getProfileImageUrl());
        }
        user.setActive(true);
        user.setEmailVerified(true);
        userRepository.save(user);

        String loginToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());

        return ResponseEntity.ok(LoginResponseDTO.builder()
                .token(loginToken)
                .userId(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(user.getRole())
                .profileImageUrl(user.getProfileImageUrl())
                .build());
    }
}
