package com.ExpenseTracker.app.chat.service;

import com.ExpenseTracker.app.chat.presentation.dto.ChatRequestDTO;
import com.ExpenseTracker.app.chat.presentation.dto.ChatResponseDTO;
import com.ExpenseTracker.app.chat.presentation.dto.ConversationDTO;

import java.util.List;
import java.util.UUID;

public interface IChatService {

    ConversationDTO createConversation(UUID userId, String firstMessage);

    ChatResponseDTO sendMessage(UUID userId, UUID conversationId, ChatRequestDTO request);

    List<ConversationDTO> getConversations(UUID userId);

    ConversationDTO getConversation(UUID userId, UUID conversationId);

    void deleteConversation(UUID userId, UUID conversationId);
}
