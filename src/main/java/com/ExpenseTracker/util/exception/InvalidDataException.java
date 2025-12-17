package com.ExpenseTracker.util.exception;
import lombok.Getter;

@Getter
public class InvalidDataException extends RuntimeException {

    private final String message;


    public InvalidDataException(String message) {
        this.message = message;
    }
}
