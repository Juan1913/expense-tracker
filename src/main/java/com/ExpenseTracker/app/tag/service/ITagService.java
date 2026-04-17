package com.ExpenseTracker.app.tag.service;

import com.ExpenseTracker.app.tag.presentation.dto.CreateTagDTO;
import com.ExpenseTracker.app.tag.presentation.dto.TagDTO;

import java.util.List;
import java.util.UUID;

public interface ITagService {

    TagDTO create(CreateTagDTO dto, UUID userId);

    List<TagDTO> findAllByUser(UUID userId);

    void delete(UUID id, UUID userId);
}
