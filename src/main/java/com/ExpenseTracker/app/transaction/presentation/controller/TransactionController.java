package com.ExpenseTracker.app.transaction.presentation.controller;

import com.ExpenseTracker.app.transaction.presentation.dto.CreateTransactionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionImportResultDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionSummaryDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.UpdateTransactionDTO;
import com.ExpenseTracker.app.transaction.service.ITransactionImportExportService;
import com.ExpenseTracker.app.transaction.service.ITransactionService;
import com.ExpenseTracker.infrastructure.security.SecurityUtils;
import com.ExpenseTracker.util.enums.TransactionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Gestión de transacciones")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final ITransactionService transactionService;
    private final ITransactionImportExportService importExportService;
    private final SecurityUtils securityUtils;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("date", "amount", "createdAt");

    @PostMapping
    @Operation(summary = "Crear nueva transacción")
    public ResponseEntity<TransactionDTO> create(@Valid @RequestBody CreateTransactionDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.create(dto, securityUtils.getCurrentUserId()));
    }

    @GetMapping
    @Operation(summary = "Listar transacciones — filtros: type, accountId, categoryId, fromDate, toDate, minAmount, maxAmount, search + page/size/sortBy/sortDir")
    public ResponseEntity<Page<TransactionDTO>> findAll(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "date";
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(dir, field));

        return ResponseEntity.ok(transactionService.findAllFiltered(
                securityUtils.getCurrentUserId(),
                type, accountId, categoryId,
                fromDate, toDate, minAmount, maxAmount, search,
                pageable));
    }

    @GetMapping("/summary")
    @Operation(summary = "Agregados (ingresos/gastos/balance) sobre todo el conjunto filtrado, no solo la página actual")
    public ResponseEntity<TransactionSummaryDTO> summary(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(transactionService.aggregates(
                securityUtils.getCurrentUserId(),
                type, accountId, categoryId,
                fromDate, toDate, minAmount, maxAmount, search));
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

    @GetMapping(value = "/export.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Exportar transacciones filtradas a Excel (.xlsx)")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String search) {

        byte[] xlsx = importExportService.exportToExcel(
                securityUtils.getCurrentUserId(),
                type, accountId, categoryId,
                fromDate, toDate, minAmount, maxAmount, search);

        String filename = "transacciones-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".xlsx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importar transacciones desde Excel/CSV. dryRun=true devuelve preview sin guardar.")
    public ResponseEntity<TransactionImportResultDTO> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean autoCreateAccounts) {
        return ResponseEntity.ok(importExportService.importFromFile(
                securityUtils.getCurrentUserId(), file, dryRun, autoCreateAccounts));
    }
}
