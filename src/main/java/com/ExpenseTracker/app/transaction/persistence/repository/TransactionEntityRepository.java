package com.ExpenseTracker.app.transaction.persistence.repository;

import com.ExpenseTracker.app.transaction.persistence.entity.TransactionEntity;
import com.ExpenseTracker.util.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionEntityRepository
        extends JpaRepository<TransactionEntity, UUID>,
                JpaSpecificationExecutor<TransactionEntity> {

    Page<TransactionEntity> findByUser_IdOrderByDateDesc(UUID userId, Pageable pageable);

    Page<TransactionEntity> findByUser_IdAndTypeOrderByDateDesc(UUID userId, TransactionType type, Pageable pageable);

    Optional<TransactionEntity> findByIdAndUser_Id(UUID id, UUID userId);

    long countByUser_Id(UUID userId);

    long countByAccount_Id(UUID accountId);

    long countByCategory_Id(UUID categoryId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionEntity t " +
           "WHERE t.user.id = :userId AND t.type = :type")
    BigDecimal sumAmountByUserIdAndType(@Param("userId") UUID userId,
                                        @Param("type") TransactionType type);

    List<TransactionEntity> findByUser_IdAndDateBetweenOrderByDateAsc(
            UUID userId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT t.category.name, SUM(t.amount) FROM TransactionEntity t " +
           "WHERE t.user.id = :userId AND t.type = 'EXPENSE' " +
           "GROUP BY t.category.name ORDER BY SUM(t.amount) DESC")
    List<Object[]> findExpensesByCategoryForUser(@Param("userId") UUID userId);

    @Query("SELECT t.category.id, t.category.name, SUM(t.amount) FROM TransactionEntity t " +
           "WHERE t.user.id = :userId AND t.type = 'EXPENSE' " +
           "AND t.date >= :start AND t.date < :end " +
           "GROUP BY t.category.id, t.category.name")
    List<Object[]> findExpensesByCategoryAndPeriod(@Param("userId") UUID userId,
                                                    @Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end);

    // Dynamic filtering + aggregates live in the service layer via
    // JpaSpecificationExecutor + CriteriaBuilder (see TransactionSpecs).

    // ── Cascade soft-delete (unchanged) ─────────────────────────────────────
    @Modifying
    @Query("UPDATE TransactionEntity t SET t.deleted = true WHERE t.account.id = :accountId")
    int softDeleteByAccount(@Param("accountId") UUID accountId);

    @Modifying
    @Query("UPDATE TransactionEntity t SET t.deleted = true WHERE t.category.id = :categoryId")
    int softDeleteByCategory(@Param("categoryId") UUID categoryId);

    @Modifying
    @Query(value = "UPDATE transactions SET deleted = false WHERE account_id = :accountId", nativeQuery = true)
    int restoreByAccount(@Param("accountId") UUID accountId);

    @Modifying
    @Query(value = "UPDATE transactions SET deleted = false WHERE category_id = :categoryId", nativeQuery = true)
    int restoreByCategory(@Param("categoryId") UUID categoryId);

    @Modifying
    @Query(value = "DELETE FROM transactions WHERE account_id = :accountId", nativeQuery = true)
    int hardDeleteByAccount(@Param("accountId") UUID accountId);

    @Modifying
    @Query(value = "DELETE FROM transactions WHERE category_id = :categoryId", nativeQuery = true)
    int hardDeleteByCategory(@Param("categoryId") UUID categoryId);
}
