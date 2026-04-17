package com.ExpenseTracker.app.account.persistence.repository;

import com.ExpenseTracker.app.account.persistence.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountEntityRepository extends JpaRepository<AccountEntity, UUID> {

    List<AccountEntity> findByUser_Id(UUID userId);

    Optional<AccountEntity> findByIdAndUser_Id(UUID id, UUID userId);
}
