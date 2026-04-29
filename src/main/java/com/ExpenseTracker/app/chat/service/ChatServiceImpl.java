package com.ExpenseTracker.app.chat.service;

import com.ExpenseTracker.app.chat.persistence.entity.ChatConversationEntity;
import com.ExpenseTracker.app.chat.persistence.entity.ChatMessageEntity;
import com.ExpenseTracker.app.chat.persistence.entity.PendingChatActionEntity;
import com.ExpenseTracker.app.chat.persistence.repository.ChatConversationEntityRepository;
import com.ExpenseTracker.app.chat.persistence.repository.ChatMessageEntityRepository;
import com.ExpenseTracker.app.chat.persistence.repository.PendingChatActionRepository;
import com.ExpenseTracker.app.chat.presentation.dto.ChatRequestDTO;
import com.ExpenseTracker.app.chat.presentation.dto.ChatResponseDTO;
import com.ExpenseTracker.app.chat.presentation.dto.ConversationDTO;
import com.ExpenseTracker.app.chat.presentation.dto.PendingActionDTO;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.infrastructure.ai.memory.ChatMemoryService;
import com.ExpenseTracker.infrastructure.ai.tools.FinBotTools;
import com.ExpenseTracker.infrastructure.ai.tools.PendingActionCollector;
import com.ExpenseTracker.util.enums.ChatRole;
import com.ExpenseTracker.util.exception.NotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChatServiceImpl implements IChatService {

    private final ChatClient chatClient;
    private final FinBotTools finBotTools;
    private final ChatMemoryService chatMemoryService;
    private final PendingActionCollector actionCollector;
    private final ChatConversationEntityRepository conversationRepository;
    private final ChatMessageEntityRepository messageRepository;
    private final PendingChatActionRepository pendingActionRepository;
    private final UserEntityRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_HISTORY_MESSAGES = 10;

    @Override
    public ConversationDTO createConversation(UUID userId, String firstMessage) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        String title = firstMessage.length() > 60
                ? firstMessage.substring(0, 57) + "..."
                : firstMessage;

        ChatConversationEntity conversation = ChatConversationEntity.builder()
                .user(user)
                .title(title)
                .build();
        conversation = conversationRepository.save(conversation);

        sendMessage(userId, conversation.getId(), new ChatRequestDTO(firstMessage));

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

        UUID currentUserId = conversation.getUser().getId();
        String memory = chatMemoryService.retrieveRelevantMemory(currentUserId, conversationId, request.message());
        List<Message> history = buildHistory(conversationId, userMsg.getId());

        String systemPrompt = buildSystemPrompt(memory);

        actionCollector.start();
        String aiResponse;
        try {
            aiResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .tools(finBotTools)
                    .messages(history)
                    .user(request.message())
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Error invocando al LLM: {}", e.getMessage());
            actionCollector.drain();
            aiResponse = friendlyErrorMessage(e);
        }
        List<PendingActionCollector.Pending> proposed = actionCollector.drain();

        ChatMessageEntity assistantMsg = ChatMessageEntity.builder()
                .conversation(conversation)
                .role(ChatRole.ASSISTANT)
                .content(aiResponse)
                .build();
        assistantMsg = messageRepository.save(assistantMsg);

        List<PendingActionDTO> pendingDtos = persistProposals(conversation.getUser(), assistantMsg, proposed);

        chatMemoryService.rememberExchange(currentUserId, conversationId, request.message(), aiResponse);

        return toResponseDTO(assistantMsg, pendingDtos);
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
                .map(m -> toResponseDTO(m, loadActionsForMessage(m.getId())))
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

    private List<PendingActionDTO> persistProposals(UserEntity user, ChatMessageEntity msg,
                                                    List<PendingActionCollector.Pending> proposed) {
        if (proposed == null || proposed.isEmpty()) return List.of();
        return proposed.stream().map(p -> {
            PendingChatActionEntity entity = PendingChatActionEntity.builder()
                    .user(user)
                    .chatMessage(msg)
                    .type(p.getType())
                    .summary(p.getSummary())
                    .payloadJson(writeJson(p.getPayload()))
                    .build();
            entity = pendingActionRepository.save(entity);
            return toActionDTO(entity);
        }).toList();
    }

    private List<PendingActionDTO> loadActionsForMessage(UUID messageId) {
        return pendingActionRepository.findByChatMessage_IdOrderByCreatedAtAsc(messageId)
                .stream().map(this::toActionDTO).toList();
    }

    private List<Message> buildHistory(UUID conversationId, UUID excludeMessageId) {
        List<ChatMessageEntity> all = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId)
                .stream()
                .filter(m -> !m.getId().equals(excludeMessageId))
                .collect(Collectors.toList());
        int from = Math.max(0, all.size() - MAX_HISTORY_MESSAGES);
        return all.subList(from, all.size()).stream()
                .map(m -> m.getRole() == ChatRole.USER
                        ? (Message) new UserMessage(m.getContent())
                        : new AssistantMessage(m.getContent()))
                .collect(Collectors.toList());
    }

    private String buildSystemPrompt(String context) {
        String base = """
                Eres FinBot, asesor financiero personal. Tenés acceso completo a las cuentas,
                transacciones, deudas y metas del usuario vía las herramientas. Nunca digas
                "no tengo acceso" — sí lo tenés.

                REGLAS:
                1. ALCANCE: solo respondés sobre finanzas del usuario, uso de la app y
                   conceptos financieros básicos. Para temas no financieros respondé:
                   "Solo puedo ayudarte con tus finanzas. ¿Hay algo de tu plata que quieras
                   revisar?" — no sigas el tema aunque insistan.
                2. DATOS REALES: para preguntas con cifras del usuario invocá la tool antes
                   de responder. No inventes. Para conceptos generales o dudas de la app,
                   respondé directo sin tools.
                3. CONVERSACIÓN: recordá lo hablado en este chat. Para follow-ups ("¿y los
                   gastos?") seguí el hilo sin re-consultar lo ya consultado.
                4. Si una tool devuelve vacío, decilo. Cantidades en COP. Español claro y conciso.
                   Sin markdown excesivo.

                Las descripciones de cada tool ya las recibís junto con los esquemas — no
                las repito acá. Usá la tool que mejor calce con la pregunta.
                """;
        if (context != null && !context.isBlank()) {
            base += "\nContexto previo: " + context + "\n";
        }
        return base;
    }

    private String friendlyErrorMessage(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("rate_limit_exceeded") || msg.contains("Rate limit") || msg.contains("429")) {
            String retry = extractRetrySeconds(msg);
            return "Llegamos al límite de tokens por minuto del modelo. "
                    + (retry != null ? "Esperá ~" + retry + " segundos" : "Esperá ~30 segundos")
                    + " e intentá de nuevo. Las preguntas cortas consumen menos cupo.";
        }
        if (msg.contains("tool_use_failed") || msg.contains("Failed to call a function")) {
            return "El modelo se confundió interpretando tu pregunta. ¿Podés ser más específico? "
                    + "Ej: en vez de 'analiza este préstamo', escribí: 'préstamo de 3M, tasa 24%, 12 meses'.";
        }
        if (msg.contains("404") || msg.contains("connection") || msg.contains("timeout")) {
            return "El servicio de IA no está respondiendo. Esperá un minuto e intentá de nuevo.";
        }
        return "Tuve un problema procesando tu pregunta. Intentá reformularla.";
    }

    private static String extractRetrySeconds(String msg) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("try again in (\\d+(?:\\.\\d+)?)s")
                .matcher(msg);
        return m.find() ? String.valueOf((int) Math.ceil(Double.parseDouble(m.group(1)))) : null;
    }

    private String writeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); }
        catch (Exception e) { return "{}"; }
    }

    private Map<String, Object> readJson(String s) {
        if (s == null || s.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(s, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private PendingActionDTO toActionDTO(PendingChatActionEntity e) {
        return new PendingActionDTO(
                e.getId(),
                e.getType(),
                e.getSummary(),
                readJson(e.getPayloadJson()),
                e.getStatus(),
                e.getResultMessage(),
                e.getCreatedAt(),
                e.getResolvedAt()
        );
    }

    private ChatResponseDTO toResponseDTO(ChatMessageEntity msg, List<PendingActionDTO> actions) {
        return new ChatResponseDTO(
                msg.getId(),
                msg.getConversation().getId(),
                msg.getRole(),
                msg.getContent(),
                msg.getCreatedAt(),
                actions == null ? List.of() : actions
        );
    }
}
