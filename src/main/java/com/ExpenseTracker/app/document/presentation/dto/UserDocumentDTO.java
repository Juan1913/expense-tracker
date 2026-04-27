package com.ExpenseTracker.app.document.presentation.dto;

import com.ExpenseTracker.util.enums.DocumentStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDocumentDTO {

    private UUID id;
    private String name;
    private String contentType;
    private Long sizeBytes;
    private Integer chunkCount;
    private DocumentStatus status;
    private String errorMessage;
    private LocalDateTime createdAt;
}
