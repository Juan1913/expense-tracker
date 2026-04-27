package com.ExpenseTracker.app.debt.persistence.repository;

import com.ExpenseTracker.app.debt.persistence.entity.DebtEntity;
import com.ExpenseTracker.util.enums.DebtStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DebtEntityRepository extends JpaRepository<DebtEntity, UUID> {

    List<DebtEntity> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    List<DebtEntity> findByUser_IdAndStatusOrderByCreatedAtDesc(UUID userId, DebtStatus status);

    Optional<DebtEntity> findByIdAndUser_Id(UUID id, UUID userId);
}
