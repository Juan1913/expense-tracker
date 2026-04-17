package com.ExpenseTracker.app.chat.persistence.repository;

import com.ExpenseTracker.app.chat.persistence.entity.ChatConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatConversationEntityRepository extends JpaRepository<ChatConversationEntity, UUID> {

    List<ChatConversationEntity> findByUser_IdOrderByUpdatedAtDesc(UUID userId);

    Optional<ChatConversationEntity> findByIdAndUser_Id(UUID id, UUID userId);
}
