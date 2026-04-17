package com.ExpenseTracker.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET =
            "test-jwt-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256-algorithm";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", 86400000L);
    }

    // ── generateToken ───────────────────────────────────────────────────────────

    @Test
    void generateToken_subjectIsUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@test.com", "USER");

        assertThat(jwtService.extractUserId(token)).isEqualTo(userId.toString());
    }

    @Test
    void generateToken_containsEmailAndRoleClaims() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@test.com", "ADMIN");

        assertThat(jwtService.extractClaim(token, "email")).isEqualTo("user@test.com");
        assertThat(jwtService.extractClaim(token, "role")).isEqualTo("ADMIN");
    }

    @Test
    void generateToken_isNotExpiredWhenFresh() {
        String token = jwtService.generateToken(UUID.randomUUID(), "u@t.com", "USER");
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    // ── generateSetupToken ──────────────────────────────────────────────────────

    @Test
    void generateSetupToken_subjectIsUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateSetupToken(userId);

        assertThat(jwtService.extractUserId(token)).isEqualTo(userId.toString());
    }

    @Test
    void generateSetupToken_containsPurposeClaim() {
        String token = jwtService.generateSetupToken(UUID.randomUUID());
        assertThat(jwtService.extractClaim(token, "purpose")).isEqualTo("setup");
    }

    @Test
    void generateSetupToken_isNotExpiredWhenFresh() {
        String token = jwtService.generateSetupToken(UUID.randomUUID());
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    // ── isSetupToken ────────────────────────────────────────────────────────────

    @Test
    void isSetupToken_returnsTrueForSetupToken() {
        String token = jwtService.generateSetupToken(UUID.randomUUID());
        assertThat(jwtService.isSetupToken(token)).isTrue();
    }

    @Test
    void isSetupToken_returnsFalseForNormalToken() {
        String token = jwtService.generateToken(UUID.randomUUID(), "u@t.com", "USER");
        assertThat(jwtService.isSetupToken(token)).isFalse();
    }

    // ── extractUserId ───────────────────────────────────────────────────────────

    @Test
    void extractUserId_returnsCorrectUUID() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "u@t.com", "USER");

        String extracted = jwtService.extractUserId(token);

        assertThat(extracted).isEqualTo(userId.toString());
        assertThatCode(() -> UUID.fromString(extracted)).doesNotThrowAnyException();
    }
}
