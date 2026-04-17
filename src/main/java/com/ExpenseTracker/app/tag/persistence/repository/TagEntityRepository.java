package com.ExpenseTracker.app.tag.persistence.repository;

import com.ExpenseTracker.app.tag.persistence.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagEntityRepository extends JpaRepository<TagEntity, UUID> {

    List<TagEntity> findByUser_Id(UUID userId);

    Optional<TagEntity> findByIdAndUser_Id(UUID id, UUID userId);
}
