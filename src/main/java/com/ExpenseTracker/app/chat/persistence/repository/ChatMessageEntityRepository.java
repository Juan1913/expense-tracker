package com.ExpenseTracker.app.chat.persistence.repository;

import com.ExpenseTracker.app.chat.persistence.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageEntityRepository extends JpaRepository<ChatMessageEntity, UUID> {

    List<ChatMessageEntity> findByConversation_IdOrderByCreatedAtAsc(UUID conversationId);
}
