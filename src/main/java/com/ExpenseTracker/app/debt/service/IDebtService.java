package com.ExpenseTracker.app.debt.service;

import com.ExpenseTracker.app.debt.presentation.dto.CreateDebtDTO;
import com.ExpenseTracker.app.debt.presentation.dto.DebtDTO;
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

    /**
     * Compara las tres estrategias clásicas (mínimo, snowball, avalanche)
     * para el portafolio de deudas activas del usuario, dado un presupuesto
     * mensual fijo de pago. Si extraBudget es null, se usa Σ(min) sin extra.
     */
    StrategyComparisonDTO compareStrategies(UUID userId, BigDecimal extraBudget);
}
