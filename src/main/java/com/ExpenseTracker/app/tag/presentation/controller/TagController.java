package com.ExpenseTracker.app.tag.presentation.controller;

import com.ExpenseTracker.app.tag.presentation.dto.CreateTagDTO;
import com.ExpenseTracker.app.tag.presentation.dto.TagDTO;
import com.ExpenseTracker.app.tag.service.ITagService;
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
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Tag(name = "Tags", description = "Gestión de etiquetas")
@SecurityRequirement(name = "bearerAuth")
public class TagController {

    private final ITagService tagService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @Operation(summary = "Crear nueva etiqueta")
    public ResponseEntity<TagDTO> create(@Valid @RequestBody CreateTagDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tagService.create(dto, securityUtils.getCurrentUserId()));
    }

    @GetMapping
    @Operation(summary = "Listar etiquetas del usuario")
    public ResponseEntity<List<TagDTO>> findAll() {
        return ResponseEntity.ok(tagService.findAllByUser(securityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar etiqueta")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        tagService.delete(id, securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
