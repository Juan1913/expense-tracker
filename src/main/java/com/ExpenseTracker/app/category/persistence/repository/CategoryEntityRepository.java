package com.ExpenseTracker.app.category.persistence.repository;

import com.ExpenseTracker.app.category.persistence.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryEntityRepository extends JpaRepository<CategoryEntity, UUID> {

    List<CategoryEntity> findByUser_Id(UUID userId);

    Optional<CategoryEntity> findByIdAndUser_Id(UUID id, UUID userId);

    List<CategoryEntity> findByUser_IdAndType(UUID userId, String type);
}
