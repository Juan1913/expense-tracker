package com.ExpenseTracker.app.chat.presentation.dto;

import com.ExpenseTracker.util.enums.ChatActionStatus;
import com.ExpenseTracker.util.enums.ChatActionType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record PendingActionDTO(
        UUID id,
        ChatActionType type,
        String summary,
        Map<String, Object> payload,
        ChatActionStatus status,
        String resultMessage,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {}
