package com.ExpenseTracker.infrastructure.ai.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persiste cada exchange (pregunta + respuesta) como un documento en
 * vector_store con metadata.type = "conversation_message", así futuras
 * conversaciones pueden recordar lo hablado antes vía búsqueda semántica.
 *
 * No hace una llamada extra al LLM — solo embeddings (que son gratis si
 * Ollama corre local). Todo asíncrono para no bloquear el thread del chat.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMemoryService {

    private static final String TYPE_KEY = "type";
    private static final String TYPE_VALUE = "conversation_message";
    private static final int MAX_MEMORY_RESULTS = 3;
    private static final int MAX_REPLY_PREVIEW_CHARS = 400;

    private final VectorStore vectorStore;

    @Async
    public void rememberExchange(UUID userId, UUID conversationId, String userMessage, String assistantReply) {
        try {
            String preview = assistantReply.length() > MAX_REPLY_PREVIEW_CHARS
                    ? assistantReply.substring(0, MAX_REPLY_PREVIEW_CHARS) + "…"
                    : assistantReply;
            String text = "Usuario preguntó: " + userMessage + "\nFinBot respondió: " + preview;

            Map<String, Object> meta = new HashMap<>();
            meta.put("userId", userId.toString());
            meta.put("conversationId", conversationId.toString());
            meta.put(TYPE_KEY, TYPE_VALUE);
            meta.put("timestamp", java.time.Instant.now().toString());

            vectorStore.add(List.of(new Document(text, meta)));
        } catch (Exception e) {
            log.warn("No se pudo guardar memoria de chat para usuario {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Recupera hasta {@value #MAX_MEMORY_RESULTS} fragmentos de conversaciones
     * pasadas relevantes al mensaje actual. Excluye la conversación en curso
     * para no devolver el chat mismo.
     */
    public String retrieveRelevantMemory(UUID userId, UUID currentConversationId, String query) {
        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            var filter = b.and(
                    b.eq("userId", userId.toString()),
                    b.eq(TYPE_KEY, TYPE_VALUE)
            ).build();

            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(MAX_MEMORY_RESULTS + 2)
                            .filterExpression(filter)
                            .build()
            );
            if (results == null || results.isEmpty()) return "";

            String currentId = currentConversationId.toString();
            return results.stream()
                    .filter(d -> !currentId.equals(String.valueOf(d.getMetadata().get("conversationId"))))
                    .limit(MAX_MEMORY_RESULTS)
                    .map(Document::getText)
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            log.warn("No se pudo recuperar memoria de chat para usuario {}: {}", userId, e.getMessage());
            return "";
        }
    }
}
