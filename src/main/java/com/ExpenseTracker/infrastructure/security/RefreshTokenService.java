package com.ExpenseTracker.infrastructure.security;

import com.ExpenseTracker.app.user.persistence.entity.RefreshTokenEntity;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RNG = new SecureRandom();

    private final RefreshTokenRepository repository;

    @Value("${app.jwt.refresh-expiration:2592000000}")
    private long refreshExpirationMs;

    @Transactional
    public String issue(UserEntity user) {
        String raw = randomToken();
        RefreshTokenEntity entity = RefreshTokenEntity.builder()
                .tokenHash(hash(raw))
                .user(user)
                .expiresAt(LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000L))
                .build();
        repository.save(entity);
        return raw;
    }

    @Transactional
    public Optional<RefreshTokenEntity> consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        Optional<RefreshTokenEntity> maybe = repository.findByTokenHashAndRevokedFalse(hash(rawToken));
        if (maybe.isEmpty()) return Optional.empty();
        RefreshTokenEntity token = maybe.get();
        if (token.isExpired()) return Optional.empty();
        token.setRevoked(true);
        repository.save(token);
        return Optional.of(token);
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId);
    }

    @Transactional
    public int cleanupOld() {
        return repository.deleteExpiredOrRevoked(LocalDateTime.now().minusDays(7));
    }

    private static String randomToken() {
        byte[] bytes = new byte[48];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
