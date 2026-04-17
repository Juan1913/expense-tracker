package com.ExpenseTracker.util.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCatalog {

    // ========== USERS ==========
    USER_NOT_FOUND("USER-404", "Usuario no encontrado"),
    USER_EMAIL_ALREADY_EXISTS("USER-409", "El correo ya está registrado"),
    USER_INVALID_DATA("USER-400", "Datos inválidos para el usuario"),
    USER_CREDENTIALS_INVALID("USER-401", "Email o contraseña incorrectos"),

    // ========== ACCOUNTS ==========
    ACCOUNT_NOT_FOUND("ACCOUNT-404", "Cuenta no encontrada"),
    ACCOUNT_INVALID_DATA("ACCOUNT-400", "Datos inválidos para la cuenta"),

    // ========== CATEGORIES ==========
    CATEGORY_NOT_FOUND("CATEGORY-404", "Categoría no encontrada"),
    CATEGORY_INVALID_DATA("CATEGORY-400", "Datos inválidos para la categoría"),

    // ========== TAGS ==========
    TAG_NOT_FOUND("TAG-404", "Etiqueta no encontrada"),

    // ========== TRANSACTIONS ==========
    TRANSACTION_NOT_FOUND("TRANSACTION-404", "Transacción no encontrada"),
    TRANSACTION_INVALID_DATA("TRANSACTION-400", "Datos inválidos para la transacción"),

    // ========== GENÉRICO ==========
    GENERIC_ERROR("GEN-500", "Error interno del servidor"),
    ACCESS_DENIED("GEN-403", "Acceso no autorizado al recurso");

    private final String code;
    private final String message;
}
