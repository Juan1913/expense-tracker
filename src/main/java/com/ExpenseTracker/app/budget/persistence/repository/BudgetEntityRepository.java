package com.ExpenseTracker.app.budget.persistence.repository;

import com.ExpenseTracker.app.budget.persistence.entity.BudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetEntityRepository extends JpaRepository<BudgetEntity, UUID> {

    List<BudgetEntity> findByUser_IdAndMonthAndYear(UUID userId, int month, int year);

    List<BudgetEntity> findByUser_IdOrderByYearDescMonthDesc(UUID userId);

    Optional<BudgetEntity> findByUser_IdAndCategory_IdAndMonthAndYear(UUID userId, UUID categoryId, int month, int year);

    Optional<BudgetEntity> findByIdAndUser_Id(UUID id, UUID userId);
}
