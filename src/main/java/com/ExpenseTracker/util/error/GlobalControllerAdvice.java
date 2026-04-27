package com.ExpenseTracker.util.error;

import com.ExpenseTracker.util.exception.AlreadyExistsException;
import com.ExpenseTracker.util.exception.InvalidDataException;
import com.ExpenseTracker.util.exception.NotFoundException;
import com.ExpenseTracker.util.exception.NotSaveException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalControllerAdvice {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NotFoundException.class)
    public ErrorResponse handleNotFound(NotFoundException ex) {
        return ErrorResponse.builder()
                .code(ErrorCatalog.USER_NOT_FOUND.getCode())
                .status(HttpStatus.NOT_FOUND)
                .message(ex.getMessage())
                .timeStamp(LocalDateTime.now())
                .build();
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(AlreadyExistsException.class)
    public ErrorResponse handleAlreadyExists(AlreadyExistsException ex) {
        return ErrorResponse.builder()
                .code(ErrorCatalog.USER_EMAIL_ALREADY_EXISTS.getCode())
                .status(HttpStatus.CONFLICT)
                .message(ex.getMessage())
                .timeStamp(LocalDateTime.now())
                .build();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidDataException.class)
    public ErrorResponse handleInvalidData(InvalidDataException ex) {
        return ErrorResponse.builder()
                .code(ErrorCatalog.USER_INVALID_DATA.getCode())
                .status(HttpStatus.BAD_REQUEST)
                .message(ex.getMessage())
                .timeStamp(LocalDateTime.now())
                .build();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return ErrorResponse.builder()
                .code(ErrorCatalog.USER_INVALID_DATA.getCode())
                .status(HttpStatus.BAD_REQUEST)
                .message(ex.getMessage())
                .timeStamp(LocalDateTime.now())
                .build();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ErrorResponse handleValidationErrors(MethodArgumentNotValidException ex) {
        BindingResult result = ex.getBindingResult();
        return ErrorResponse.builder()
                .code(ErrorCatalog.USER_INVALID_DATA.getCode())
                .status(HttpStatus.BAD_REQUEST)
                .message(ErrorCatalog.USER_INVALID_DATA.getMessage())
                .detailMessages(result.getFieldErrors().stream()
                        .map(DefaultMessageSourceResolvable::getDefaultMessage)
                        .collect(Collectors.toList()))
                .timeStamp(LocalDateTime.now())
                .build();
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(NotSaveException.class)
    public ErrorResponse handleNotSave(NotSaveException ex) {
        return ErrorResponse.builder()
                .code(ErrorCatalog.GENERIC_ERROR.getCode())
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .message(ex.getMessage())
                .timeStamp(LocalDateTime.now())
                .build();
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(AuthenticationException.class)
    public ErrorResponse handleAuthentication(AuthenticationException ex) {
        return ErrorResponse.builder()
                .code(ErrorCatalog.USER_CREDENTIALS_INVALID.getCode())
                .status(HttpStatus.UNAUTHORIZED)
                .message(ErrorCatalog.USER_CREDENTIALS_INVALID.getMessage())
                .timeStamp(LocalDateTime.now())
                .build();
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException.class)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return ErrorResponse.builder()
                .code(ErrorCatalog.ACCESS_DENIED.getCode())
                .status(HttpStatus.FORBIDDEN)
                .message(ErrorCatalog.ACCESS_DENIED.getMessage())
                .timeStamp(LocalDateTime.now())
                .build();
    }

    /**
     * AI provider returned a non-retryable error (most commonly 429 quota exceeded,
     * 401 invalid key, 403 billing). We surface a friendly message instead of a 500.
     */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler({ NonTransientAiException.class, TransientAiException.class })
    public ErrorResponse handleAiProviderError(Exception ex) {
        String raw = ex.getMessage() == null ? "" : ex.getMessage();
        String friendly;
        if (raw.contains("429") || raw.contains("RESOURCE_EXHAUSTED") || raw.contains("quota")) {
            friendly = "El asistente IA alcanzó su límite de uso. Intenta de nuevo en unos minutos.";
        } else if (raw.contains("401") || raw.contains("403") || raw.contains("API key")) {
            friendly = "El asistente IA no está configurado correctamente (API key inválida o sin permisos).";
        } else {
            friendly = "El asistente IA no está disponible en este momento.";
        }
        return ErrorResponse.builder()
                .code(ErrorCatalog.GENERIC_ERROR.getCode())
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .message(friendly)
                .timeStamp(LocalDateTime.now())
                .build();
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorResponse handleGenericError(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ErrorResponse.builder()
                .code(ErrorCatalog.GENERIC_ERROR.getCode())
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .message(ErrorCatalog.GENERIC_ERROR.getMessage())
                .detailMessages(Collections.singletonList(ex.getMessage()))
                .timeStamp(LocalDateTime.now())
                .build();
    }
}
