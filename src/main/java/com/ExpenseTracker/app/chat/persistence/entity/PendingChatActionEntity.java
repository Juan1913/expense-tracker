package com.ExpenseTracker.app.chat.persistence.entity;

import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.util.enums.ChatActionStatus;
import com.ExpenseTracker.util.enums.ChatActionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pending_chat_actions")
public class PendingChatActionEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id")
    private ChatMessageEntity chatMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatActionType type;

    @Column(nullable = false, length = 300)
    private String summary;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatActionStatus status;

    @Column(name = "result_message", length = 500)
    private String resultMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = ChatActionStatus.PENDING;
    }
}
