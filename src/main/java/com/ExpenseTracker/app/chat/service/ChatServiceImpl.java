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

    private static final int MAX_HISTORY_MESSAGES = 20;

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
        } finally {
            // garantizamos drain incluso si tira excepción.
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
        String base = """
                Eres FinBot, asesor financiero personal del usuario. TIENES ACCESO COMPLETO
                a sus cuentas, transacciones, deudas, presupuestos y metas a través de las
                herramientas listadas abajo. NUNCA digas "no tengo acceso a tus cuentas" —
                sí lo tienes. Si necesitas un dato, invocá la herramienta correspondiente.

                REGLA #1 (no negociable): Para CUALQUIER pregunta sobre dinero del usuario
                (saldos, capacidad de compra, gastos, ingresos, deudas, ahorro, comparaciones
                temporales) DEBES invocar PRIMERO una o más herramientas de lectura. Sólo
                después de tener datos reales, formulás la respuesta. Está prohibido inventar
                cifras o decir genericidades sin haber consultado.

                HERRAMIENTAS DE LECTURA:
                  • getAccountBalances → saldos por cuenta.
                  • getNetWorthSummary → patrimonio total (operativo + ahorro − deuda de tarjetas).
                  • creditCardOverview → tarjetas: deuda, cupo, % uso, tasa.
                  • searchTransactions → transacciones con filtros (tipo, fecha, monto, texto).
                  • getMonthlySummary → ingresos/gastos/ahorro neto de un mes específico.
                  • getCategorySpending → gasto en una categoría durante N meses.
                  • getActiveDebts → deudas estructuradas (préstamos) activas.
                  • compareDebtPayoffStrategies → snowball vs avalanche dado un extra mensual.
                  • recommendDebtPayoffPlan → recomienda estrategia con base en cashflow real.
                  • simulateRedirectingExpense → "si redirigís X de una categoría a deudas, ¿cuántos meses te ahorrás?".
                  • analyzeProspectiveDebt → analiza un préstamo nuevo antes de aceptarlo (cuota, intereses, % del ingreso).
                  • getActiveWishlist → metas de ahorro activas.
                  • listUserDocuments / searchUserDocuments → PDFs/textos que subió el usuario.

                HERRAMIENTAS DE ACCIÓN (proponen, no ejecutan — el usuario confirma con un botón):
                  • proposeExpense → si pide registrar un gasto.
                  • proposeIncome → si pide registrar un ingreso.
                  • proposeTransfer → si pide mover plata entre cuentas (incluye 'mover a ahorro').

                EJEMPLOS de cuándo llamar herramientas:
                  • "¿cómo está mi salud financiera?" o "¿puedo darme un gusto?" →
                    getNetWorthSummary + getActiveDebts + getMonthlySummary del mes actual.
                    Analizá saldo, deudas y ritmo de ahorro antes de opinar.
                  • "¿cuánta plata tengo?" → getNetWorthSummary.
                  • "¿en qué gasto más?" → searchTransactions con type=EXPENSE del mes en curso.
                  • "¿gasté mucho en restaurantes?" → getCategorySpending("restaurantes", 1).
                  • "anota que gasté 50k en mercado" → proposeExpense.
                  • "pasá 200k a ahorro" → proposeTransfer.

                Reglas adicionales:
                  • Si una tool devuelve vacío, decílo explícitamente ("no encontré movimientos…").
                  • Cantidades en COP salvo que indique otra moneda.
                  • Respondé siempre en español, claro y conciso. Sin markdown excesivo.
                """;
        if (context != null && !context.isBlank()) {
            base += "\nCONTEXTO ADICIONAL DEL USUARIO (resumido):\n" + context + "\n";
        }
        return base;
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
