package com.ExpenseTracker.app.tag.mapper;

import com.ExpenseTracker.app.tag.persistence.entity.TagEntity;
import com.ExpenseTracker.app.tag.presentation.dto.CreateTagDTO;
import com.ExpenseTracker.app.tag.presentation.dto.TagDTO;
import org.mapstruct.*;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface TagMapper {

    TagDTO toDTO(TagEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    TagEntity toEntity(CreateTagDTO dto);
}
