package com.ExpenseTracker.app.account.presentation.controller;

import com.ExpenseTracker.app.account.presentation.dto.AccountDTO;
import com.ExpenseTracker.app.account.presentation.dto.AccountImpactDTO;
import com.ExpenseTracker.app.account.presentation.dto.CreateAccountDTO;
import com.ExpenseTracker.app.account.presentation.dto.UpdateAccountDTO;
import com.ExpenseTracker.app.account.service.IAccountService;
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
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Gestión de cuentas bancarias")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final IAccountService accountService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @Operation(summary = "Crear nueva cuenta")
    public ResponseEntity<AccountDTO> create(@Valid @RequestBody CreateAccountDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.create(dto, securityUtils.getCurrentUserId()));
    }

    @GetMapping
    @Operation(summary = "Listar cuentas — filtros opcionales: search, currency, sortBy, sortDir")
    public ResponseEntity<List<AccountDTO>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(accountService.findAllByUserFiltered(
                securityUtils.getCurrentUserId(), search, currency, sortBy, sortDir));
    }

    @GetMapping("/trash")
    @Operation(summary = "Listar cuentas en la papelera")
    public ResponseEntity<List<AccountDTO>> findTrash() {
        return ResponseEntity.ok(accountService.findTrashByUser(securityUtils.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener cuenta por ID")
    public ResponseEntity<AccountDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.findById(id, securityUtils.getCurrentUserId()));
    }

    @GetMapping("/{id}/impact")
    @Operation(summary = "Vista previa de lo que se eliminará en cascada")
    public ResponseEntity<AccountImpactDTO> impact(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.impact(id, securityUtils.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar cuenta")
    public ResponseEntity<AccountDTO> update(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateAccountDTO dto) {
        return ResponseEntity.ok(accountService.update(id, dto, securityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Enviar cuenta a la papelera (soft-delete con cascada)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        accountService.delete(id, securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restaurar cuenta desde la papelera")
    public ResponseEntity<AccountDTO> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.restore(id, securityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{id}/permanent")
    @Operation(summary = "Eliminar cuenta permanentemente (irreversible, borra datos relacionados)")
    public ResponseEntity<Void> deletePermanent(@PathVariable UUID id) {
        accountService.deletePermanent(id, securityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
