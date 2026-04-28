package com.ExpenseTracker.app.debt.service;

import com.ExpenseTracker.app.debt.presentation.dto.CreateDebtDTO;
import com.ExpenseTracker.app.debt.presentation.dto.CreateDebtPaymentDTO;
import com.ExpenseTracker.app.debt.presentation.dto.DebtDTO;
import com.ExpenseTracker.app.debt.presentation.dto.DebtPaymentDTO;
import com.ExpenseTracker.app.debt.presentation.dto.DebtSummaryDTO;
import com.ExpenseTracker.app.debt.presentation.dto.StrategyComparisonDTO;
import com.ExpenseTracker.app.debt.presentation.dto.UpdateDebtDTO;
import com.ExpenseTracker.util.enums.DebtStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface IDebtService {

    DebtDTO create(CreateDebtDTO dto, UUID userId);

    List<DebtDTO> findAllByUser(UUID userId, DebtStatus status);

    DebtDTO findById(UUID id, UUID userId);

    DebtDTO update(UUID id, UpdateDebtDTO dto, UUID userId);

    void delete(UUID id, UUID userId);

    StrategyComparisonDTO compareStrategies(UUID userId, BigDecimal extraBudget);

    DebtPaymentDTO recordPayment(UUID debtId, CreateDebtPaymentDTO dto, UUID userId);

    List<DebtPaymentDTO> listPayments(UUID debtId, UUID userId);

    DebtSummaryDTO summary(UUID debtId, UUID userId);
}
