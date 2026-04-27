package com.ExpenseTracker.app.transaction.mapper;

import com.ExpenseTracker.app.transaction.persistence.entity.TransactionEntity;
import com.ExpenseTracker.app.transaction.presentation.dto.CreateTransactionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionDTO;
import org.mapstruct.*;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface TransactionMapper {

    @Mapping(source = "account.id", target = "accountId")
    @Mapping(source = "account.name", target = "accountName")
    @Mapping(source = "transferToAccount.id", target = "transferToAccountId")
    @Mapping(source = "transferToAccount.name", target = "transferToAccountName")
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    @Mapping(source = "user.id", target = "userId")
    TransactionDTO toDTO(TransactionEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "transferToAccount", ignore = true)
    @Mapping(target = "category", ignore = true)
    TransactionEntity toEntity(CreateTransactionDTO dto);
}
