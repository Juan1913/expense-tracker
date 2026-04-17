package com.ExpenseTracker.app.tag.presentation.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TagDTO {
    private UUID id;
    private String description;
    private LocalDateTime createdAt;
}
