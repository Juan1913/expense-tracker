package com.ExpenseTracker.app.transaction.service;

import com.ExpenseTracker.app.transaction.presentation.dto.TransactionImportResultDTO;
import com.ExpenseTracker.util.enums.TransactionType;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface ITransactionImportExportService {

    byte[] exportToExcel(
            UUID userId,
            TransactionType type, UUID accountId, UUID categoryId,
            LocalDateTime fromDate, LocalDateTime toDate,
            BigDecimal minAmount, BigDecimal maxAmount,
            String search);

    TransactionImportResultDTO importFromFile(UUID userId, MultipartFile file, boolean dryRun, boolean autoCreateAccounts);
}
