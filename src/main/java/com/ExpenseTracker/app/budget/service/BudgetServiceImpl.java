package com.ExpenseTracker.app.budget.service;

import com.ExpenseTracker.app.budget.mapper.BudgetMapper;
import com.ExpenseTracker.app.budget.persistence.entity.BudgetEntity;
import com.ExpenseTracker.app.budget.persistence.repository.BudgetEntityRepository;
import com.ExpenseTracker.app.budget.presentation.dto.BudgetDTO;
import com.ExpenseTracker.app.budget.presentation.dto.CreateBudgetDTO;
import com.ExpenseTracker.app.budget.presentation.dto.UpdateBudgetDTO;
import com.ExpenseTracker.app.category.persistence.entity.CategoryEntity;
import com.ExpenseTracker.app.category.persistence.repository.CategoryEntityRepository;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.util.exception.AlreadyExistsException;
import com.ExpenseTracker.util.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BudgetServiceImpl implements IBudgetService {

    private final BudgetEntityRepository budgetRepository;
    private final CategoryEntityRepository categoryRepository;
    private final UserEntityRepository userRepository;
    private final BudgetMapper budgetMapper;

    @Override
    public BudgetDTO create(CreateBudgetDTO dto, UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado con id: " + userId));

        CategoryEntity category = categoryRepository.findByIdAndUser_Id(dto.getCategoryId(), userId)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada con id: " + dto.getCategoryId()));

        if (budgetRepository.findByUser_IdAndCategory_IdAndMonthAndYear(
                userId, dto.getCategoryId(), dto.getMonth(), dto.getYear()).isPresent()) {
            throw new AlreadyExistsException(
                    "Ya existe un presupuesto para esa categoría en " + dto.getMonth() + "/" + dto.getYear());
        }

        BudgetEntity entity = budgetMapper.toEntity(dto);
        entity.setUser(user);
        entity.setCategory(category);

        return budgetMapper.toDTO(budgetRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BudgetDTO> findAllByUser(UUID userId, Integer month, Integer year) {
        if (month != null && year != null) {
            return budgetRepository.findByUser_IdAndMonthAndYear(userId, month, year)
                    .stream().map(budgetMapper::toDTO).toList();
        }
        return budgetRepository.findByUser_IdOrderByYearDescMonthDesc(userId)
                .stream().map(budgetMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetDTO findById(UUID id, UUID userId) {
        return budgetRepository.findByIdAndUser_Id(id, userId)
                .map(budgetMapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Presupuesto no encontrado con id: " + id));
    }

    @Override
    public BudgetDTO update(UUID id, UpdateBudgetDTO dto, UUID userId) {
        BudgetEntity entity = budgetRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Presupuesto no encontrado con id: " + id));
        budgetMapper.updateEntityFromDTO(dto, entity);
        return budgetMapper.toDTO(budgetRepository.save(entity));
    }

    @Override
    public void delete(UUID id, UUID userId) {
        BudgetEntity entity = budgetRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Presupuesto no encontrado con id: " + id));
        budgetRepository.delete(entity);
    }
}
