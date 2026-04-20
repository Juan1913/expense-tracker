package com.ExpenseTracker.app.category.service;

import com.ExpenseTracker.app.category.presentation.dto.CategoryDTO;
import com.ExpenseTracker.app.category.presentation.dto.CategoryImpactDTO;
import com.ExpenseTracker.app.category.presentation.dto.CreateCategoryDTO;
import com.ExpenseTracker.app.category.presentation.dto.UpdateCategoryDTO;

import java.util.List;
import java.util.UUID;

public interface ICategoryService {

    CategoryDTO create(CreateCategoryDTO dto, UUID userId);

    List<CategoryDTO> findAllByUser(UUID userId);

    List<CategoryDTO> findAllByUserAndType(UUID userId, String type);

    CategoryDTO findById(UUID id, UUID userId);

    CategoryDTO update(UUID id, UpdateCategoryDTO dto, UUID userId);

    void delete(UUID id, UUID userId);

    CategoryImpactDTO impact(UUID id, UUID userId);

    List<CategoryDTO> findTrashByUser(UUID userId);

    CategoryDTO restore(UUID id, UUID userId);

    void deletePermanent(UUID id, UUID userId);
}
