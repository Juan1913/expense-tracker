package com.ExpenseTracker.app.budget.service;

import com.ExpenseTracker.app.budget.presentation.dto.BudgetDTO;
import com.ExpenseTracker.app.budget.presentation.dto.CreateBudgetDTO;
import com.ExpenseTracker.app.budget.presentation.dto.UpdateBudgetDTO;

import java.util.List;
import java.util.UUID;

public interface IBudgetService {

    BudgetDTO create(CreateBudgetDTO dto, UUID userId);

    List<BudgetDTO> findAllByUser(UUID userId, Integer month, Integer year);

    BudgetDTO findById(UUID id, UUID userId);

    BudgetDTO update(UUID id, UpdateBudgetDTO dto, UUID userId);

    void delete(UUID id, UUID userId);
}
