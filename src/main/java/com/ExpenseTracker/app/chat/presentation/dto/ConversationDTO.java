package com.ExpenseTracker.app.chat.presentation.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ConversationDTO(
        UUID id,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ChatResponseDTO> messages
) {}
