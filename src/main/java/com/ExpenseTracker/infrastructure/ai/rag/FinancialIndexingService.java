package com.ExpenseTracker.infrastructure.ai.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialIndexingService {

    private final VectorStore vectorStore;
    private final FinancialDocumentBuilder documentBuilder;
    private final JdbcTemplate jdbcTemplate;

    @Async
    @Transactional(readOnly = true)
    public void reindexUser(UUID userId) {
        log.info("Re-indexando datos financieros para usuario {}", userId);
        try {
            deleteUserDocuments(userId);
            var docs = documentBuilder.buildDocuments(userId);
            if (!docs.isEmpty()) {
                vectorStore.add(docs);
            }
            log.info("Re-indexación completada: {} documentos para usuario {}", docs.size(), userId);
        } catch (Exception e) {
            log.error("Error en re-indexación para usuario {}: {}", userId, e.getMessage());
        }
    }

    private void deleteUserDocuments(UUID userId) {
        jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'userId' = ?",
                userId.toString()
        );
    }
}
