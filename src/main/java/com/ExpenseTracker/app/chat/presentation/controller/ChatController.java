package com.ExpenseTracker.app.chat.presentation.controller;

import com.ExpenseTracker.app.chat.presentation.dto.ChatRequestDTO;
import com.ExpenseTracker.app.chat.presentation.dto.ChatResponseDTO;
import com.ExpenseTracker.app.chat.presentation.dto.ConversationDTO;
import com.ExpenseTracker.app.chat.service.IChatService;
import com.ExpenseTracker.infrastructure.ai.rag.FinancialIndexingService;
import com.ExpenseTracker.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat FinBot", description = "Asesor financiero personal con IA")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    private final IChatService chatService;
    private final FinancialIndexingService indexingService;
    private final SecurityUtils securityUtils;

    @PostMapping("/conversations")
    @Operation(summary = "Crear conversación con primer mensaje",
            description = "Crea una nueva conversación y envía el primer mensaje a FinBot")
    public ResponseEntity<ConversationDTO> createConversation(@Valid @RequestBody ChatRequestDTO request) {
        UUID userId = securityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.createConversation(userId, request.message()));
    }

    @GetMapping("/conversations")
    @Operation(summary = "Listar conversaciones del usuario")
    public ResponseEntity<List<ConversationDTO>> getConversations() {
        UUID userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(chatService.getConversations(userId));
    }

    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "Obtener conversación con todos sus mensajes")
    public ResponseEntity<ConversationDTO> getConversation(@PathVariable UUID conversationId) {
        UUID userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(chatService.getConversation(userId, conversationId));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Enviar mensaje en una conversación existente")
    public ResponseEntity<ChatResponseDTO> sendMessage(
            @PathVariable UUID conversationId,
            @Valid @RequestBody ChatRequestDTO request) {
        UUID userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(chatService.sendMessage(userId, conversationId, request));
    }

    @DeleteMapping("/conversations/{conversationId}")
    @Operation(summary = "Eliminar conversación")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID conversationId) {
        UUID userId = securityUtils.getCurrentUserId();
        chatService.deleteConversation(userId, conversationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/index")
    @Operation(summary = "Re-indexar datos financieros del usuario",
            description = "Actualiza los embeddings en el vector store con los datos financieros más recientes")
    public ResponseEntity<Void> reindex() {
        UUID userId = securityUtils.getCurrentUserId();
        indexingService.reindexUser(userId);
        return ResponseEntity.accepted().build();
    }
}
