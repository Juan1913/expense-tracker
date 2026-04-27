package com.ExpenseTracker.app.document.service;

import com.ExpenseTracker.app.document.persistence.entity.UserDocumentEntity;
import com.ExpenseTracker.app.document.persistence.repository.UserDocumentRepository;
import com.ExpenseTracker.app.document.presentation.dto.UserDocumentDTO;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.infrastructure.storage.StorageService;
import com.ExpenseTracker.util.enums.DocumentStatus;
import com.ExpenseTracker.util.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserDocumentServiceImpl implements IUserDocumentService {

    private static final long MAX_BYTES = 15L * 1024 * 1024; // 15 MB
    private static final String STORAGE_FOLDER_PREFIX = "documents";

    private final UserDocumentRepository repository;
    private final UserEntityRepository userRepository;
    private final StorageService storageService;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public UserDocumentDTO upload(MultipartFile file, UUID userId) {
        validate(file);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        String key = storageService.upload(file, STORAGE_FOLDER_PREFIX + "/" + userId);

        UserDocumentEntity entity = UserDocumentEntity.builder()
                .name(Objects.requireNonNullElse(file.getOriginalFilename(), "documento.pdf"))
                .storageKey(key)
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .status(DocumentStatus.PROCESSING)
                .user(user)
                .build();
        entity = repository.save(entity);

        try {
            String text = extractText(file);
            if (text.isBlank()) {
                throw new IllegalStateException("El documento no contiene texto extraíble");
            }
            int chunks = embedAndStore(text, userId, entity.getId(), entity.getName());
            entity.setChunkCount(chunks);
            entity.setStatus(DocumentStatus.READY);
        } catch (Exception e) {
            log.error("Falló indexado de documento {}: {}", entity.getId(), e.getMessage(), e);
            entity.setStatus(DocumentStatus.FAILED);
            entity.setErrorMessage(truncate(e.getMessage(), 500));
        }

        return toDTO(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDocumentDTO> findAllByUser(UUID userId) {
        return repository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDocumentDTO findById(UUID id, UUID userId) {
        return repository.findByIdAndUser_Id(id, userId)
                .map(this::toDTO)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
    }

    @Override
    public void delete(UUID id, UUID userId) {
        UserDocumentEntity entity = repository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));

        // Borrar chunks del vector store
        try {
            jdbcTemplate.update(
                    "DELETE FROM vector_store WHERE metadata->>'documentId' = ?",
                    id.toString()
            );
        } catch (Exception e) {
            log.warn("No se pudieron borrar chunks del documento {}: {}", id, e.getMessage());
        }

        // Borrar archivo del storage
        try {
            storageService.delete(entity.getStorageKey());
        } catch (Exception e) {
            log.warn("No se pudo borrar archivo {} de storage: {}", entity.getStorageKey(), e.getMessage());
        }

        repository.delete(entity);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("El archivo supera el límite de 15 MB");
        }
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase();
        String type = Objects.requireNonNullElse(file.getContentType(), "");
        boolean isPdf = name.endsWith(".pdf") || type.equals("application/pdf");
        boolean isText = name.endsWith(".txt") || name.endsWith(".md") || type.startsWith("text/");
        if (!isPdf && !isText) {
            throw new IllegalArgumentException("Solo se aceptan PDF o archivos de texto");
        }
    }

    private String extractText(MultipartFile file) throws Exception {
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase();
        if (name.endsWith(".pdf") || "application/pdf".equals(file.getContentType())) {
            try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(file.getInputStream()))) {
                return new PDFTextStripper().getText(doc);
            }
        }
        return new String(file.getBytes());
    }

    private int embedAndStore(String text, UUID userId, UUID documentId, String documentName) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        Document raw = new Document(text);
        List<Document> chunks = splitter.split(raw);

        List<Document> withMetadata = chunks.stream()
                .map(c -> {
                    Map<String, Object> meta = new HashMap<>(c.getMetadata());
                    meta.put("userId", userId.toString());
                    meta.put("type", "document");
                    meta.put("documentId", documentId.toString());
                    meta.put("documentName", documentName);
                    return new Document(c.getText(), meta);
                })
                .toList();

        vectorStore.add(withMetadata);
        return withMetadata.size();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private UserDocumentDTO toDTO(UserDocumentEntity e) {
        return UserDocumentDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .contentType(e.getContentType())
                .sizeBytes(e.getSizeBytes())
                .chunkCount(e.getChunkCount())
                .status(e.getStatus())
                .errorMessage(e.getErrorMessage())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
