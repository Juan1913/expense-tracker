package com.ExpenseTracker.app.category.persistence.repository;

import com.ExpenseTracker.app.category.persistence.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryEntityRepository extends JpaRepository<CategoryEntity, UUID> {

    List<CategoryEntity> findByUser_Id(UUID userId);

    Optional<CategoryEntity> findByIdAndUser_Id(UUID id, UUID userId);

    List<CategoryEntity> findByUser_IdAndType(UUID userId, String type);

    // ── Trash ──
    @Query(value = "SELECT * FROM categories WHERE user_id = :userId AND deleted = true ORDER BY name",
           nativeQuery = true)
    List<CategoryEntity> findDeletedByUser(@Param("userId") UUID userId);

    @Query(value = "SELECT * FROM categories WHERE id = :id AND user_id = :userId", nativeQuery = true)
    Optional<CategoryEntity> findByIdAndUserIncludingDeleted(@Param("id") UUID id,
                                                              @Param("userId") UUID userId);

    @Modifying
    @Query(value = "UPDATE categories SET deleted = false WHERE id = :id AND user_id = :userId",
           nativeQuery = true)
    int restoreByIdAndUser(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM categories WHERE id = :id AND user_id = :userId",
           nativeQuery = true)
    int hardDeleteByIdAndUser(@Param("id") UUID id, @Param("userId") UUID userId);
}
