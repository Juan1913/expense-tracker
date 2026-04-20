package com.ExpenseTracker.app.transaction.service;

import com.ExpenseTracker.app.transaction.presentation.dto.CreateTransactionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionSummaryDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.UpdateTransactionDTO;
import com.ExpenseTracker.util.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface ITransactionService {

    TransactionDTO create(CreateTransactionDTO dto, UUID userId);

    Page<TransactionDTO> findAllByUser(UUID userId, TransactionType type, Pageable pageable);

    /** Paginated filtered listing used by the transactions page. */
    Page<TransactionDTO> findAllFiltered(
            UUID userId,
            TransactionType type, UUID accountId, UUID categoryId,
            LocalDateTime fromDate, LocalDateTime toDate,
            BigDecimal minAmount, BigDecimal maxAmount,
            String search,
            Pageable pageable);

    /** Aggregates (income, expense, balance) over the entire filter set (all pages). */
    TransactionSummaryDTO aggregates(
            UUID userId,
            TransactionType type, UUID accountId, UUID categoryId,
            LocalDateTime fromDate, LocalDateTime toDate,
            BigDecimal minAmount, BigDecimal maxAmount,
            String search);

    TransactionDTO findById(UUID id, UUID userId);

    TransactionDTO update(UUID id, UpdateTransactionDTO dto, UUID userId);

    void delete(UUID id, UUID userId);
}
