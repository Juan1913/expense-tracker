package com.ExpenseTracker.app.chat.service;

import com.ExpenseTracker.app.chat.persistence.entity.ChatConversationEntity;
import com.ExpenseTracker.app.chat.persistence.entity.ChatMessageEntity;
import com.ExpenseTracker.app.chat.persistence.repository.ChatConversationEntityRepository;
import com.ExpenseTracker.app.chat.persistence.repository.ChatMessageEntityRepository;
import com.ExpenseTracker.app.chat.presentation.dto.ChatRequestDTO;
import com.ExpenseTracker.app.chat.presentation.dto.ChatResponseDTO;
import com.ExpenseTracker.app.chat.presentation.dto.ConversationDTO;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.infrastructure.ai.rag.FinancialIndexingService;
import com.ExpenseTracker.util.enums.ChatRole;
import com.ExpenseTracker.util.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChatServiceImpl implements IChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final FinancialIndexingService indexingService;
    private final ChatConversationEntityRepository conversationRepository;
    private final ChatMessageEntityRepository messageRepository;
    private final UserEntityRepository userRepository;

    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int RAG_TOP_K = 8;

    @Override
    public ConversationDTO createConversation(UUID userId, String firstMessage) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        indexingService.reindexUser(userId);

        String title = firstMessage.length() > 60
                ? firstMessage.substring(0, 57) + "..."
                : firstMessage;

        ChatConversationEntity conversation = ChatConversationEntity.builder()
                .user(user)
                .title(title)
                .build();
        conversation = conversationRepository.save(conversation);

        ChatResponseDTO assistantResponse = sendMessage(userId, conversation.getId(),
                new ChatRequestDTO(firstMessage));

        return getConversation(userId, conversation.getId());
    }

    @Override
    public ChatResponseDTO sendMessage(UUID userId, UUID conversationId, ChatRequestDTO request) {
        ChatConversationEntity conversation = conversationRepository
                .findByIdAndUser_Id(conversationId, userId)
                .orElseThrow(() -> new NotFoundException("Conversación no encontrada"));

        ChatMessageEntity userMsg = ChatMessageEntity.builder()
                .conversation(conversation)
                .role(ChatRole.USER)
                .content(request.message())
                .build();
        messageRepository.save(userMsg);

        String context = retrieveFinancialContext(userId, request.message());
        List<Message> history = buildHistory(conversationId, userMsg.getId());

        String systemPrompt = buildSystemPrompt(context);

        String aiResponse = chatClient.prompt()
                .system(systemPrompt)
                .messages(history)
                .user(request.message())
                .call()
                .content();

        ChatMessageEntity assistantMsg = ChatMessageEntity.builder()
                .conversation(conversation)
                .role(ChatRole.ASSISTANT)
                .content(aiResponse)
                .build();
        assistantMsg = messageRepository.save(assistantMsg);

        return toResponseDTO(assistantMsg);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationDTO> getConversations(UUID userId) {
        return conversationRepository.findByUser_IdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(c -> new ConversationDTO(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt(), List.of()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationDTO getConversation(UUID userId, UUID conversationId) {
        ChatConversationEntity conversation = conversationRepository
                .findByIdAndUser_Id(conversationId, userId)
                .orElseThrow(() -> new NotFoundException("Conversación no encontrada"));

        List<ChatResponseDTO> messages = messageRepository
                .findByConversation_IdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());

        return new ConversationDTO(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                messages
        );
    }

    @Override
    public void deleteConversation(UUID userId, UUID conversationId) {
        ChatConversationEntity conversation = conversationRepository
                .findByIdAndUser_Id(conversationId, userId)
                .orElseThrow(() -> new NotFoundException("Conversación no encontrada"));
        conversationRepository.delete(conversation);
    }

    private String retrieveFinancialContext(UUID userId, String query) {
        try {
            var filter = new FilterExpressionBuilder().eq("userId", userId.toString()).build();
            var results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(RAG_TOP_K)
                            .filterExpression(filter)
                            .build()
            );
            if (results.isEmpty()) return "";
            return results.stream()
                    .map(doc -> doc.getText())
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("No se pudo recuperar contexto financiero para usuario {}: {}", userId, e.getMessage());
            return "";
        }
    }

    private List<Message> buildHistory(UUID conversationId, UUID excludeMessageId) {
        return messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId)
                .stream()
                .filter(m -> !m.getId().equals(excludeMessageId))
                .limit(MAX_HISTORY_MESSAGES)
                .map(m -> m.getRole() == ChatRole.USER
                        ? (Message) new UserMessage(m.getContent())
                        : new AssistantMessage(m.getContent()))
                .collect(Collectors.toList());
    }

    private String buildSystemPrompt(String context) {
        if (context == null || context.isBlank()) {
            return """
                    Eres FinBot, un asesor financiero personal inteligente y empático.
                    Ayuda al usuario con sus finanzas. Si no tienes datos del usuario disponibles,
                    sugiere que use el endpoint /api/v1/chat/index para actualizar su información.
                    Responde siempre en español, de forma clara y directa.
                    """;
        }
        return """
                Eres FinBot, un asesor financiero personal inteligente y empático.
                Tu objetivo es ayudar al usuario a tomar decisiones financieras informadas
                basándote en sus datos reales que se muestran a continuación.

                DATOS FINANCIEROS DEL USUARIO:
                %s

                Responde siempre en español, de forma clara y directa.
                Cuando hagas recomendaciones, basa tus respuestas en los datos anteriores.
                Si el usuario pregunta si puede comprar algo, analiza sus ingresos, gastos,
                saldo en cuentas y metas de ahorro antes de responder.
                """.formatted(context);
    }

    private ChatResponseDTO toResponseDTO(ChatMessageEntity msg) {
        return new ChatResponseDTO(
                msg.getId(),
                msg.getConversation().getId(),
                msg.getRole(),
                msg.getContent(),
                msg.getCreatedAt()
        );
    }
}
