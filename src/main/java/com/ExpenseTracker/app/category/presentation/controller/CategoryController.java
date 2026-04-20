package com.ExpenseTracker.app.category.presentation.controller;

import com.ExpenseTracker.app.category.presentation.dto.CategoryDTO;
import com.ExpenseTracker.app.category.presentation.dto.CategoryImpactDTO;
import com.ExpenseTracker.app.category.presentation.dto.CreateCategoryDTO;
import com.ExpenseTracker.app.category.presentation.dto.UpdateCategoryDTO;
import com.ExpenseTracker.app.category.service.ICategoryService;
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
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Gestión de categorías de transacciones")
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {

    private final ICategoryService categoryService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @Operation(summary = "Crear nueva categoría")
    public ResponseEntity<CategoryDTO> create(@Valid @RequestBody CreateCategoryDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.create(dto, securityUtils.getCurrentUserId()));
    }

    @GetMapping
    @Operation(summary = "Listar categorías — filtro opcional ?type=EXPENSE|INCOME")
    public ResponseEntity<List<CategoryDTO>> findAll(@RequestParam(required = false) String type) {
        UUID userId = securityUtils.getCurrentUserId();
        List<CategoryDTO> result = type != null
                ? categoryService.findAllByUserAndType(userId, type)
                : categoryService.findAllByUser(userId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/trash")
    @Operation(summary = "Listar categorías en la papelera")
    public ResponseEntity<List<CategoryDTO>> findTrash() {
        return ResponseEntity.ok(categoryService.findTrashByUser(securityUtils.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener categoría por ID")
    public ResponseEntity<CategoryDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.findById(id, securityUtils.getCurrentUserId()));
    }

    @GetMapping("/{id}/impact")
    @Operation(summary = "Vista previa de lo que se eliminará en cascada")
    public ResponseEntity<CategoryImpactDTO> impact(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.impact(id, securityUtils.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar categoría")
    public ResponseEntity<CategoryDTO> update(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateCategoryDTO dto) {
        return ResponseEntity.ok(categoryService.update(id, dto, securityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Enviar categoría a la papelera (soft-delete con cascada)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id, securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restaurar categoría desde la papelera")
    public ResponseEntity<CategoryDTO> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.restore(id, securityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{id}/permanent")
    @Operation(summary = "Eliminar categoría permanentemente (irreversible)")
    public ResponseEntity<Void> deletePermanent(@PathVariable UUID id) {
        categoryService.deletePermanent(id, securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
