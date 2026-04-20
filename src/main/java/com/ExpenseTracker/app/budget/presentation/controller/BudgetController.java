package com.ExpenseTracker.app.budget.presentation.controller;

import com.ExpenseTracker.app.budget.presentation.dto.BudgetComparisonDTO;
import com.ExpenseTracker.app.budget.presentation.dto.BudgetDTO;
import com.ExpenseTracker.app.budget.presentation.dto.CreateBudgetDTO;
import com.ExpenseTracker.app.budget.presentation.dto.UpdateBudgetDTO;
import com.ExpenseTracker.app.budget.service.IBudgetService;
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
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
@Tag(name = "Budgets", description = "Gestión de presupuestos por categoría")
@SecurityRequirement(name = "bearerAuth")
public class BudgetController {

    private final IBudgetService budgetService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @Operation(summary = "Crear presupuesto mensual para una categoría")
    public ResponseEntity<BudgetDTO> create(@Valid @RequestBody CreateBudgetDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(budgetService.create(dto, securityUtils.getCurrentUserId()));
    }

    @GetMapping
    @Operation(summary = "Listar presupuestos — filtro opcional ?month=1-12&year=2024")
    public ResponseEntity<List<BudgetDTO>> findAll(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(budgetService.findAllByUser(securityUtils.getCurrentUserId(), month, year));
    }

    @GetMapping("/comparison")
    @Operation(summary = "Presupuestos con gasto real por categoría para un mes/año")
    public ResponseEntity<List<BudgetComparisonDTO>> findComparison(
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(
                budgetService.findComparison(securityUtils.getCurrentUserId(), month, year));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener presupuesto por ID")
    public ResponseEntity<BudgetDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(budgetService.findById(id, securityUtils.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar monto del presupuesto")
    public ResponseEntity<BudgetDTO> update(@PathVariable UUID id,
                                            @Valid @RequestBody UpdateBudgetDTO dto) {
        return ResponseEntity.ok(budgetService.update(id, dto, securityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar presupuesto")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        budgetService.delete(id, securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
