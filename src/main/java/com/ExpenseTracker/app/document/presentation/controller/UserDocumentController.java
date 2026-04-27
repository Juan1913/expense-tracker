package com.ExpenseTracker.app.document.presentation.controller;

import com.ExpenseTracker.app.document.presentation.dto.UserDocumentDTO;
import com.ExpenseTracker.app.document.service.IUserDocumentService;
import com.ExpenseTracker.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Documentos del usuario indexados para RAG en FinBot")
@SecurityRequirement(name = "bearerAuth")
public class UserDocumentController {

    private final IUserDocumentService documentService;
    private final SecurityUtils securityUtils;

    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "Subir un PDF/TXT al contexto privado del usuario para que FinBot pueda consultarlo")
    public ResponseEntity<UserDocumentDTO> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.upload(file, securityUtils.getCurrentUserId()));
    }

    @GetMapping
    @Operation(summary = "Listar documentos del usuario")
    public ResponseEntity<List<UserDocumentDTO>> findAll() {
        return ResponseEntity.ok(documentService.findAllByUser(securityUtils.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener metadata de un documento")
    public ResponseEntity<UserDocumentDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.findById(id, securityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar documento (incluye chunks del vector store y archivo en storage)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id, securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
