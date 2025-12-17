package com.ExpenseTracker.util.error;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ErrorResponse {
    private String code;
    private HttpStatus status;
    private String message;
    private List<String> detailMessages;
    private LocalDateTime timeStamp;
}
