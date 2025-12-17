package com.ExpenseTracker.util.exception;

public class NotSaveException extends RuntimeException {

    public NotSaveException(String message) {
        super(message);
    }

    public NotSaveException(String message, Throwable cause) {
        super(message, cause);
    }
}
