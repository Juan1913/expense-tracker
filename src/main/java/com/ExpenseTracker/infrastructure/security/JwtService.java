package com.ExpenseTracker.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    private static final long SETUP_TOKEN_EXPIRATION = 15 * 60 * 1000L; // 15 min

    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.expiration}")
    private long expiration;

    public String generateToken(UUID userId, String email, String role) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateSetupToken(UUID userId) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("purpose", "setup")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + SETUP_TOKEN_EXPIRATION))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractClaim(String token, String claimName) {
        return extractClaim(token, claims -> claims.get(claimName, String.class));
    }

    public boolean isSetupToken(String token) {
        return "setup".equals(extractClaim(token, "purpose"));
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return extractUserId(token).equals(userDetails.getUsername())
                && !extractClaim(token, Claims::getExpiration).before(new Date());
    }

    public boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(Jwts.parserBuilder()
                .setSigningKey(signingKey())
                .build()
                .parseClaimsJws(token)
                .getBody());
    }

    private Key signingKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }
}
