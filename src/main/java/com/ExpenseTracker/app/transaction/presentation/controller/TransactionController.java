package com.ExpenseTracker.app.transaction.presentation.controller;

import com.ExpenseTracker.app.transaction.presentation.dto.CreateTransactionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.UpdateTransactionDTO;
import com.ExpenseTracker.app.transaction.service.ITransactionService;
import com.ExpenseTracker.infrastructure.security.SecurityUtils;
import com.ExpenseTracker.util.enums.TransactionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Gestión de transacciones")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final ITransactionService transactionService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @Operation(summary = "Crear nueva transacción")
    public ResponseEntity<TransactionDTO> create(@Valid @RequestBody CreateTransactionDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.create(dto, securityUtils.getCurrentUserId()));
    }

    @GetMapping
    @Operation(summary = "Listar transacciones paginadas — filtro opcional ?type=EXPENSE|INCOME")
    public ResponseEntity<Page<TransactionDTO>> findAll(
            @RequestParam(required = false) TransactionType type,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                transactionService.findAllByUser(securityUtils.getCurrentUserId(), type, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener transacción por ID")
    public ResponseEntity<TransactionDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.findById(id, securityUtils.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar transacción")
    public ResponseEntity<TransactionDTO> update(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateTransactionDTO dto) {
        return ResponseEntity.ok(transactionService.update(id, dto, securityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar transacción")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        transactionService.delete(id, securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
