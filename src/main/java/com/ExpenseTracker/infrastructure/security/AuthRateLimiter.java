package com.ExpenseTracker.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AuthRateLimiter {

    private static final int MAX_ATTEMPTS_PER_MINUTE = 8;

    private final Cache<String, AtomicInteger> buckets = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(20_000)
            .build();

    public boolean tryAcquire(HttpServletRequest request, String action) {
        String key = action + ":" + clientIp(request);
        AtomicInteger count = buckets.get(key, k -> new AtomicInteger(0));
        int current = count.incrementAndGet();
        return current <= MAX_ATTEMPTS_PER_MINUTE;
    }

    public int remaining(HttpServletRequest request, String action) {
        AtomicInteger count = buckets.getIfPresent(action + ":" + clientIp(request));
        if (count == null) return MAX_ATTEMPTS_PER_MINUTE;
        return Math.max(0, MAX_ATTEMPTS_PER_MINUTE - count.get());
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return request.getRemoteAddr();
    }
}
