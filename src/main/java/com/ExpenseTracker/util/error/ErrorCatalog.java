package com.ExpenseTracker.util.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCatalog {




    // ========== USERS ==========
    USER_NOT_FOUND("USER-404", "Usuario no encontrado"),
    USER_EMAIL_ALREADY_EXISTS("INVALID-DATA-409", "El correo ya está registrado"),
    USER_INVALID_DATA("USER-400", "Datos inválidos para el usuario"),


    // ========== GENÉRICO ==========
    GENERIC_ERROR("GEN-500", "Error interno del servidor");


    private final String code;
    private final String message;

    public String getMessage() {
        return message;
    }

    public String getCode() {
        return code;
    }
}