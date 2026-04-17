package com.ExpenseTracker.app.category.presentation.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CategoryDTO {
    private UUID id;
    private String name;
    private String description;
    private String type;
    private LocalDateTime createdAt;
}
