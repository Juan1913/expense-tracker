package com.ExpenseTracker.app.chat.persistence.repository;

import com.ExpenseTracker.app.chat.persistence.entity.PendingChatActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingChatActionRepository extends JpaRepository<PendingChatActionEntity, UUID> {

    Optional<PendingChatActionEntity> findByIdAndUser_Id(UUID id, UUID userId);

    List<PendingChatActionEntity> findByChatMessage_IdOrderByCreatedAtAsc(UUID chatMessageId);
}
