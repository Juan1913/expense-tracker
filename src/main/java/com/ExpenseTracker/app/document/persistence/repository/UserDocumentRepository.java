package com.ExpenseTracker.app.document.persistence.repository;

import com.ExpenseTracker.app.document.persistence.entity.UserDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDocumentRepository extends JpaRepository<UserDocumentEntity, UUID> {

    List<UserDocumentEntity> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<UserDocumentEntity> findByIdAndUser_Id(UUID id, UUID userId);
}
