package com.ExpenseTracker.app.user.persistence.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationTokenTest {

    @Test
    void isExpired_whenExpiresAtIsInFuture_returnsFalse() {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        assertThat(token.isExpired()).isFalse();
    }

    @Test
    void isExpired_whenExpiresAtIsInPast_returnsTrue() {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build();

        assertThat(token.isExpired()).isTrue();
    }

    @Test
    void isExpired_justExpired_returnsTrue() {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        assertThat(token.isExpired()).isTrue();
    }

    @Test
    void defaultUsed_isFalse() {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        assertThat(token.isUsed()).isFalse();
    }
}
