package com.ExpenseTracker.app.document.persistence.entity;

import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.util.enums.DocumentStatus;
import com.ExpenseTracker.util.persistence.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_documents")
@SQLDelete(sql = "UPDATE user_documents SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
public class UserDocumentEntity extends SoftDeletableEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    /** Key del archivo en Wasabi (e.g. "documents/<userId>/<uuid>.pdf"). */
    @Column(name = "storage_key", nullable = false, length = 400)
    private String storageKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = DocumentStatus.PROCESSING;
    }
}
