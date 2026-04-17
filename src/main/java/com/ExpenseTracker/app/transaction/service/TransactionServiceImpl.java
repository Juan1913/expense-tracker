package com.ExpenseTracker.app.transaction.service;

import com.ExpenseTracker.app.account.persistence.entity.AccountEntity;
import com.ExpenseTracker.app.account.persistence.repository.AccountEntityRepository;
import com.ExpenseTracker.app.category.persistence.entity.CategoryEntity;
import com.ExpenseTracker.app.category.persistence.repository.CategoryEntityRepository;
import com.ExpenseTracker.app.transaction.mapper.TransactionMapper;
import com.ExpenseTracker.app.transaction.persistence.entity.TransactionEntity;
import com.ExpenseTracker.app.transaction.persistence.repository.TransactionEntityRepository;
import com.ExpenseTracker.app.transaction.presentation.dto.CreateTransactionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.UpdateTransactionDTO;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.util.enums.TransactionType;
import com.ExpenseTracker.util.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TransactionServiceImpl implements ITransactionService {

    private final TransactionEntityRepository transactionRepository;
    private final AccountEntityRepository accountRepository;
    private final CategoryEntityRepository categoryRepository;
    private final UserEntityRepository userRepository;
    private final TransactionMapper transactionMapper;

    @Override
    public TransactionDTO create(CreateTransactionDTO dto, UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado con id: " + userId));
        AccountEntity account = accountRepository.findByIdAndUser_Id(dto.getAccountId(), userId)
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada con id: " + dto.getAccountId()));
        CategoryEntity category = categoryRepository.findByIdAndUser_Id(dto.getCategoryId(), userId)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada con id: " + dto.getCategoryId()));

        TransactionEntity entity = transactionMapper.toEntity(dto);
        entity.setUser(user);
        entity.setAccount(account);
        entity.setCategory(category);

        return transactionMapper.toDTO(transactionRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionDTO> findAllByUser(UUID userId, TransactionType type, Pageable pageable) {
        if (type != null) {
            return transactionRepository
                    .findByUser_IdAndTypeOrderByDateDesc(userId, type, pageable)
                    .map(transactionMapper::toDTO);
        }
        return transactionRepository
                .findByUser_IdOrderByDateDesc(userId, pageable)
                .map(transactionMapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionDTO findById(UUID id, UUID userId) {
        return transactionRepository.findByIdAndUser_Id(id, userId)
                .map(transactionMapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Transacción no encontrada con id: " + id));
    }

    @Override
    public TransactionDTO update(UUID id, UpdateTransactionDTO dto, UUID userId) {
        TransactionEntity entity = transactionRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Transacción no encontrada con id: " + id));

        if (dto.getAmount() != null) entity.setAmount(dto.getAmount());
        if (dto.getDate() != null) entity.setDate(dto.getDate());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getType() != null) entity.setType(dto.getType());

        if (dto.getAccountId() != null) {
            entity.setAccount(accountRepository.findByIdAndUser_Id(dto.getAccountId(), userId)
                    .orElseThrow(() -> new NotFoundException("Cuenta no encontrada con id: " + dto.getAccountId())));
        }
        if (dto.getCategoryId() != null) {
            entity.setCategory(categoryRepository.findByIdAndUser_Id(dto.getCategoryId(), userId)
                    .orElseThrow(() -> new NotFoundException("Categoría no encontrada con id: " + dto.getCategoryId())));
        }

        return transactionMapper.toDTO(transactionRepository.save(entity));
    }

    @Override
    public void delete(UUID id, UUID userId) {
        TransactionEntity entity = transactionRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Transacción no encontrada con id: " + id));
        transactionRepository.delete(entity);
    }
}
