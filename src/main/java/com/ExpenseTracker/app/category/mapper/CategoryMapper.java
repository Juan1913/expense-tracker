package com.ExpenseTracker.app.category.mapper;

import com.ExpenseTracker.app.category.persistence.entity.CategoryEntity;
import com.ExpenseTracker.app.category.presentation.dto.CategoryDTO;
import com.ExpenseTracker.app.category.presentation.dto.CreateCategoryDTO;
import com.ExpenseTracker.app.category.presentation.dto.UpdateCategoryDTO;
import org.mapstruct.*;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface CategoryMapper {

    CategoryDTO toDTO(CategoryEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    CategoryEntity toEntity(CreateCategoryDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    void updateEntityFromDTO(UpdateCategoryDTO dto, @MappingTarget CategoryEntity entity);
}
