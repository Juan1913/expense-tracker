package com.ExpenseTracker.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuthCookieService {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String sameSite;

    @Value("${app.cookie.domain:}")
    private String domain;

    public void writeAccessCookie(HttpServletResponse response, String token, long maxAgeMs) {
        writeCookie(response, ACCESS_TOKEN_COOKIE, token, (int) (maxAgeMs / 1000), "/");
    }

    public void writeRefreshCookie(HttpServletResponse response, String token, long maxAgeMs) {
        writeCookie(response, REFRESH_TOKEN_COOKIE, token, (int) (maxAgeMs / 1000), "/api/v1/auth");
    }

    public void clearAuthCookies(HttpServletResponse response) {
        writeCookie(response, ACCESS_TOKEN_COOKIE, "", 0, "/");
        writeCookie(response, REFRESH_TOKEN_COOKIE, "", 0, "/api/v1/auth");
    }

    public Optional<String> readAccessToken(HttpServletRequest request) {
        return readCookie(request, ACCESS_TOKEN_COOKIE);
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        return readCookie(request, REFRESH_TOKEN_COOKIE);
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return Optional.empty();
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isEmpty()) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }

    private void writeCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds, String path) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=").append(value);
        sb.append("; Path=").append(path);
        sb.append("; Max-Age=").append(maxAgeSeconds);
        sb.append("; HttpOnly");
        if (cookieSecure) sb.append("; Secure");
        if (sameSite != null && !sameSite.isBlank()) sb.append("; SameSite=").append(sameSite);
        if (domain != null && !domain.isBlank()) sb.append("; Domain=").append(domain);
        response.addHeader("Set-Cookie", sb.toString());
    }
}
