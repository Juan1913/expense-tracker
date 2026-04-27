package com.ExpenseTracker.app.debt.presentation.controller;

import com.ExpenseTracker.app.debt.presentation.dto.CreateDebtDTO;
import com.ExpenseTracker.app.debt.presentation.dto.DebtDTO;
import com.ExpenseTracker.app.debt.presentation.dto.StrategyComparisonDTO;
import com.ExpenseTracker.app.debt.presentation.dto.UpdateDebtDTO;
import com.ExpenseTracker.app.debt.service.IDebtService;
import com.ExpenseTracker.infrastructure.security.SecurityUtils;
import com.ExpenseTracker.util.enums.DebtStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/debts")
@RequiredArgsConstructor
@Tag(name = "Debts", description = "Gestión de deudas con simulación snowball / avalanche")
@SecurityRequirement(name = "bearerAuth")
public class DebtController {

    private final IDebtService debtService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @Operation(summary = "Crear nueva deuda")
    public ResponseEntity<DebtDTO> create(@Valid @RequestBody CreateDebtDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(debtService.create(dto, securityUtils.getCurrentUserId()));
    }

    @GetMapping
    @Operation(summary = "Listar deudas — filtro opcional ?status=ACTIVE|PAID_OFF|IN_DEFAULT")
    public ResponseEntity<List<DebtDTO>> findAll(@RequestParam(required = false) DebtStatus status) {
        return ResponseEntity.ok(debtService.findAllByUser(securityUtils.getCurrentUserId(), status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener deuda por ID")
    public ResponseEntity<DebtDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(debtService.findById(id, securityUtils.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar deuda o registrar pago (modificando currentBalance)")
    public ResponseEntity<DebtDTO> update(@PathVariable UUID id,
                                          @Valid @RequestBody UpdateDebtDTO dto) {
        return ResponseEntity.ok(debtService.update(id, dto, securityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar deuda (soft delete)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        debtService.delete(id, securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/strategies")
    @Operation(summary = "Comparar estrategias de pago: solo mínimos, snowball y avalanche, dado un excedente mensual opcional")
    public ResponseEntity<StrategyComparisonDTO> compareStrategies(
            @RequestParam(required = false) BigDecimal extraBudget) {
        return ResponseEntity.ok(debtService.compareStrategies(securityUtils.getCurrentUserId(), extraBudget));
    }
}
