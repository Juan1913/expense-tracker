package com.ExpenseTracker.app.auth.presentation.controller;

import com.ExpenseTracker.app.auth.presentation.dto.ForgotPasswordDTO;
import com.ExpenseTracker.app.auth.presentation.dto.LoginRequestDTO;
import com.ExpenseTracker.app.auth.presentation.dto.LoginResponseDTO;
import com.ExpenseTracker.app.auth.presentation.dto.ResetPasswordDTO;
import com.ExpenseTracker.app.auth.presentation.dto.SetupProfileDTO;
import com.ExpenseTracker.app.user.persistence.entity.EmailVerificationToken;
import com.ExpenseTracker.app.user.persistence.entity.PasswordResetToken;
import com.ExpenseTracker.app.user.persistence.entity.RefreshTokenEntity;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.EmailVerificationTokenRepository;
import com.ExpenseTracker.app.user.persistence.repository.PasswordResetTokenRepository;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.app.user.presentation.dto.CreateUserDTO;
import com.ExpenseTracker.app.user.presentation.dto.UserDTO;
import com.ExpenseTracker.app.user.service.IUserService;
import com.ExpenseTracker.infrastructure.email.EmailService;
import com.ExpenseTracker.infrastructure.security.AuthCookieService;
import com.ExpenseTracker.infrastructure.security.AuthRateLimiter;
import com.ExpenseTracker.infrastructure.security.JwtService;
import com.ExpenseTracker.infrastructure.security.RefreshTokenService;
import com.ExpenseTracker.util.exception.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Auth", description = "Registro e inicio de sesión")
public class AuthController {

    private final IUserService userService;
    private final UserEntityRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService cookieService;
    private final AuthRateLimiter rateLimiter;
    private final EmailService emailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @PostMapping("/register")
    @Operation(summary = "Registrar nuevo usuario")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody CreateUserDTO dto, HttpServletRequest request) {
        ensureRateLimit(request, "register");
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(dto));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto,
                                                  HttpServletRequest request,
                                                  HttpServletResponse response) {
        ensureRateLimit(request, "login");

        UserEntity user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new NotFoundException("Credenciales inválidas"));

        if (!user.isActive()) throw new DisabledException("Cuenta desactivada");

        if (user.getPassword() == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        issueAuthCookies(user, response);

        return ResponseEntity.ok(LoginResponseDTO.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(user.getRole())
                .build());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renueva el access token usando el refresh token (cookie HttpOnly)")
    public ResponseEntity<LoginResponseDTO> refresh(HttpServletRequest request, HttpServletResponse response) {
        String raw = cookieService.readRefreshToken(request).orElse(null);
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No hay sesión activa");
        }
        RefreshTokenEntity token = refreshTokenService.consume(raw)
                .orElseThrow(() -> {
                    cookieService.clearAuthCookies(response);
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token inválido o expirado");
                });

        UserEntity user = token.getUser();
        if (!user.isActive()) {
            cookieService.clearAuthCookies(response);
            throw new DisabledException("Cuenta desactivada");
        }
        issueAuthCookies(user, response);

        return ResponseEntity.ok(LoginResponseDTO.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(user.getRole())
                .build());
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión: revoca refresh token y limpia cookies")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request, HttpServletResponse response) {
        cookieService.readRefreshToken(request).ifPresent(refreshTokenService::consume);
        cookieService.clearAuthCookies(response);
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada"));
    }

    @GetMapping("/verify")
    @Operation(summary = "Verificar token de invitación por email")
    @Transactional(readOnly = true)
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
    @Transactional
    public ResponseEntity<LoginResponseDTO> setupProfile(@Valid @RequestBody SetupProfileDTO dto,
                                                         HttpServletResponse response) {
        if (jwtService.isTokenExpired(dto.getSetupToken())) {
            throw new BadCredentialsException("El token de configuración ha expirado");
        }
        if (!jwtService.isSetupToken(dto.getSetupToken())) {
            throw new BadCredentialsException("Token inválido");
        }

        UUID userId = UUID.fromString(jwtService.extractUserId(dto.getSetupToken()));
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        tokenRepository.findByTokenAndUsedFalse(dto.getSetupToken()).ifPresent(t -> {
            t.setUsed(true);
            tokenRepository.save(t);
        });

        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setActive(true);
        user.setEmailVerified(true);
        userRepository.save(user);

        issueAuthCookies(user, response);

        return ResponseEntity.ok(LoginResponseDTO.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(user.getRole())
                .build());
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Solicitar correo de recuperación de contraseña")
    @Transactional
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordDTO dto,
                                                              HttpServletRequest request) {
        ensureRateLimit(request, "forgot-password");
        Optional<UserEntity> maybeUser = userRepository.findByEmail(dto.getEmail());
        maybeUser.ifPresent(user -> {
            String rawToken = UUID.randomUUID().toString();
            PasswordResetToken token = PasswordResetToken.builder()
                    .token(rawToken)
                    .user(user)
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();
            resetTokenRepository.save(token);
            try {
                emailService.sendPasswordResetEmail(user.getEmail(), rawToken, frontendUrl);
            } catch (Exception e) {
                log.error("Error enviando correo de recuperación a {}: {}", user.getEmail(), e.getMessage(), e);
            }
        });

        return ResponseEntity.ok(Map.of(
                "message", "Si el correo está registrado, recibirás un enlace para restablecer tu contraseña."
        ));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Restablecer contraseña con token recibido por correo")
    @Transactional
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordDTO dto,
                                                             HttpServletRequest request) {
        ensureRateLimit(request, "reset-password");
        PasswordResetToken token = resetTokenRepository.findByTokenAndUsedFalse(dto.getToken())
                .orElseThrow(() -> new BadCredentialsException("Token inválido o ya utilizado"));

        if (token.isExpired()) {
            throw new BadCredentialsException("El enlace de recuperación ha expirado");
        }

        UserEntity user = token.getUser();
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        userRepository.save(user);

        token.setUsed(true);
        resetTokenRepository.save(token);

        refreshTokenService.revokeAllForUser(user.getId());

        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente"));
    }

    private void issueAuthCookies(UserEntity user, HttpServletResponse response) {
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = refreshTokenService.issue(user);
        cookieService.writeAccessCookie(response, accessToken, jwtService.getAccessExpirationMs());
        cookieService.writeRefreshCookie(response, refreshToken, jwtService.getRefreshExpirationMs());
    }

    private void ensureRateLimit(HttpServletRequest request, String action) {
        if (!rateLimiter.tryAcquire(request, action)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos. Esperá un minuto e intentá de nuevo.");
        }
    }
}
