package com.ExpenseTracker.util.error;

import com.ExpenseTracker.util.exception.AlreadyExistsException;
import com.ExpenseTracker.util.exception.InvalidDataException;
import com.ExpenseTracker.util.exception.NotFoundException;
import com.ExpenseTracker.util.exception.NotSaveException;
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

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorResponse handleGenericError(Exception ex) {
        return ErrorResponse.builder()
                .code(ErrorCatalog.GENERIC_ERROR.getCode())
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .message(ErrorCatalog.GENERIC_ERROR.getMessage())
                .detailMessages(Collections.singletonList(ex.getMessage()))
                .timeStamp(LocalDateTime.now())
                .build();
    }
}
