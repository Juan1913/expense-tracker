package com.ExpenseTracker.app.chat.presentation.dto;

import com.ExpenseTracker.util.enums.ChatRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatResponseDTO(
        UUID messageId,
        UUID conversationId,
        ChatRole role,
        String content,
        LocalDateTime createdAt
) {}
