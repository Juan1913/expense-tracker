package com.ExpenseTracker.app.category.service;

import com.ExpenseTracker.app.budget.persistence.repository.BudgetEntityRepository;
import com.ExpenseTracker.app.category.mapper.CategoryMapper;
import com.ExpenseTracker.app.category.persistence.entity.CategoryEntity;
import com.ExpenseTracker.app.category.persistence.repository.CategoryEntityRepository;
import com.ExpenseTracker.app.category.presentation.dto.CategoryDTO;
import com.ExpenseTracker.app.category.presentation.dto.CategoryImpactDTO;
import com.ExpenseTracker.app.category.presentation.dto.CreateCategoryDTO;
import com.ExpenseTracker.app.category.presentation.dto.UpdateCategoryDTO;
import com.ExpenseTracker.app.transaction.persistence.repository.TransactionEntityRepository;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.util.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryServiceImpl implements ICategoryService {

    private final CategoryEntityRepository categoryRepository;
    private final UserEntityRepository userRepository;
    private final TransactionEntityRepository transactionRepository;
    private final BudgetEntityRepository budgetRepository;
    private final CategoryMapper categoryMapper;

    @Override
    public CategoryDTO create(CreateCategoryDTO dto, UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado con id: " + userId));
        CategoryEntity entity = categoryMapper.toEntity(dto);
        entity.setUser(user);
        return categoryMapper.toDTO(categoryRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDTO> findAllByUser(UUID userId) {
        return categoryRepository.findByUser_Id(userId).stream()
                .map(categoryMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDTO> findAllByUserAndType(UUID userId, String type) {
        return categoryRepository.findByUser_IdAndType(userId, type).stream()
                .map(categoryMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryDTO findById(UUID id, UUID userId) {
        return categoryRepository.findByIdAndUser_Id(id, userId)
                .map(categoryMapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada con id: " + id));
    }

    @Override
    public CategoryDTO update(UUID id, UpdateCategoryDTO dto, UUID userId) {
        CategoryEntity entity = categoryRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada con id: " + id));
        categoryMapper.updateEntityFromDTO(dto, entity);
        return categoryMapper.toDTO(categoryRepository.save(entity));
    }

    @Override
    public void delete(UUID id, UUID userId) {
        CategoryEntity entity = categoryRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada con id: " + id));
        // Cascade soft-delete: transactions and budgets in this category go to trash too.
        transactionRepository.softDeleteByCategory(id);
        budgetRepository.softDeleteByCategory(id);
        categoryRepository.delete(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryImpactDTO impact(UUID id, UUID userId) {
        categoryRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada con id: " + id));
        return CategoryImpactDTO.builder()
                .transactions(transactionRepository.countByCategory_Id(id))
                .budgets(budgetRepository.countByCategory_Id(id))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDTO> findTrashByUser(UUID userId) {
        return categoryRepository.findDeletedByUser(userId).stream()
                .map(categoryMapper::toDTO)
                .toList();
    }

    @Override
    public CategoryDTO restore(UUID id, UUID userId) {
        CategoryEntity entity = categoryRepository.findByIdAndUserIncludingDeleted(id, userId)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada con id: " + id));
        categoryRepository.restoreByIdAndUser(id, userId);
        transactionRepository.restoreByCategory(id);
        budgetRepository.restoreByCategory(id);
        entity.setDeleted(false);
        return categoryMapper.toDTO(entity);
    }

    @Override
    public void deletePermanent(UUID id, UUID userId) {
        categoryRepository.findByIdAndUserIncludingDeleted(id, userId)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada con id: " + id));
        transactionRepository.hardDeleteByCategory(id);
        budgetRepository.hardDeleteByCategory(id);
        categoryRepository.hardDeleteByIdAndUser(id, userId);
    }
}
