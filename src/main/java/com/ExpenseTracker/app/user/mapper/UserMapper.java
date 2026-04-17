package com.ExpenseTracker.app.user.mapper;

import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.presentation.dto.CreateUserDTO;
import com.ExpenseTracker.app.user.presentation.dto.UpdateUserDTO;
import com.ExpenseTracker.app.user.presentation.dto.UserDTO;
import org.mapstruct.*;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UserMapper {

    UserDTO toDTO(UserEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "accounts", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "profileImageUrl", ignore = true)
    @Mapping(target = "role", constant = "USER")
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "emailVerified", constant = "true")
    UserEntity toEntity(CreateUserDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "accounts", ignore = true)
    @Mapping(target = "tags", ignore = true)
    void updateEntityFromDTO(UpdateUserDTO dto, @MappingTarget UserEntity entity);
}
