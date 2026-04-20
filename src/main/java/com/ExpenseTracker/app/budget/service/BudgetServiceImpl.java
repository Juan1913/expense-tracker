package com.ExpenseTracker.app.budget.service;

import com.ExpenseTracker.app.budget.mapper.BudgetMapper;
import com.ExpenseTracker.app.budget.persistence.entity.BudgetEntity;
import com.ExpenseTracker.app.budget.persistence.repository.BudgetEntityRepository;
import com.ExpenseTracker.app.budget.presentation.dto.BudgetComparisonDTO;
import com.ExpenseTracker.app.budget.presentation.dto.BudgetDTO;
import com.ExpenseTracker.app.budget.presentation.dto.CreateBudgetDTO;
import com.ExpenseTracker.app.budget.presentation.dto.UpdateBudgetDTO;
import com.ExpenseTracker.app.category.persistence.entity.CategoryEntity;
import com.ExpenseTracker.app.category.persistence.repository.CategoryEntityRepository;
import com.ExpenseTracker.app.transaction.persistence.repository.TransactionEntityRepository;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.util.exception.AlreadyExistsException;
import com.ExpenseTracker.util.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BudgetServiceImpl implements IBudgetService {

    private final BudgetEntityRepository budgetRepository;
    private final CategoryEntityRepository categoryRepository;
    private final TransactionEntityRepository transactionRepository;
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
    public List<BudgetComparisonDTO> findComparison(UUID userId, int month, int year) {
        List<BudgetEntity> budgets = budgetRepository.findByUser_IdAndMonthAndYear(userId, month, year);

        LocalDateTime start = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime end   = start.plusMonths(1);
        List<Object[]> rows = transactionRepository.findExpensesByCategoryAndPeriod(userId, start, end);

        Map<UUID, BigDecimal> actualByCategory = new HashMap<>();
        for (Object[] row : rows) {
            actualByCategory.put((UUID) row[0], (BigDecimal) row[2]);
        }

        return budgets.stream().map(b -> {
            UUID catId = b.getCategory().getId();
            BigDecimal actual   = actualByCategory.getOrDefault(catId, BigDecimal.ZERO);
            BigDecimal budgeted = b.getAmount();
            BigDecimal pct = budgeted.compareTo(BigDecimal.ZERO) > 0
                    ? actual.divide(budgeted, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            return BudgetComparisonDTO.builder()
                    .id(b.getId())
                    .budgeted(budgeted)
                    .actual(actual)
                    .percentage(pct)
                    .month(b.getMonth())
                    .year(b.getYear())
                    .categoryId(catId)
                    .categoryName(b.getCategory().getName())
                    .createdAt(b.getCreatedAt())
                    .build();
        }).toList();
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
