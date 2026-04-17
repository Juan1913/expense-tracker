package com.ExpenseTracker.infrastructure.security;

import com.ExpenseTracker.util.exception.InvalidDataException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecurityUtils {

    public UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new InvalidDataException("No hay usuario autenticado");
        }
        return UUID.fromString(auth.getName());
    }
}
