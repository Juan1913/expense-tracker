package com.ExpenseTracker.app.budget.persistence.repository;

import com.ExpenseTracker.app.budget.persistence.entity.BudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetEntityRepository extends JpaRepository<BudgetEntity, UUID> {

    // JOIN FETCH category: avoids LazyInitializationException when budgets are
    // loaded on an async thread (DashboardService) and their category is
    // accessed later on the request thread.
    @Query("SELECT b FROM BudgetEntity b JOIN FETCH b.category " +
           "WHERE b.user.id = :userId AND b.month = :month AND b.year = :year")
    List<BudgetEntity> findByUser_IdAndMonthAndYear(@Param("userId") UUID userId,
                                                    @Param("month") int month,
                                                    @Param("year") int year);

    List<BudgetEntity> findByUser_IdOrderByYearDescMonthDesc(UUID userId);

    Optional<BudgetEntity> findByUser_IdAndCategory_IdAndMonthAndYear(UUID userId, UUID categoryId, int month, int year);

    Optional<BudgetEntity> findByIdAndUser_Id(UUID id, UUID userId);

    // ── Count for impact preview (filtered by @SQLRestriction) ──
    long countByCategory_Id(UUID categoryId);

    // ── Cascade soft-delete ──
    @Modifying
    @Query("UPDATE BudgetEntity b SET b.deleted = true WHERE b.category.id = :categoryId")
    int softDeleteByCategory(@Param("categoryId") UUID categoryId);

    // ── Cascade restore (native bypasses @SQLRestriction) ──
    @Modifying
    @Query(value = "UPDATE budgets SET deleted = false WHERE category_id = :categoryId", nativeQuery = true)
    int restoreByCategory(@Param("categoryId") UUID categoryId);

    // ── Cascade hard-delete ──
    @Modifying
    @Query(value = "DELETE FROM budgets WHERE category_id = :categoryId", nativeQuery = true)
    int hardDeleteByCategory(@Param("categoryId") UUID categoryId);
}
