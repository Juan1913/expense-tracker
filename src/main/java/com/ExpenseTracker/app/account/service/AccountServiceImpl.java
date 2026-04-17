package com.ExpenseTracker.app.account.service;

import com.ExpenseTracker.app.account.mapper.AccountMapper;
import com.ExpenseTracker.app.account.persistence.entity.AccountEntity;
import com.ExpenseTracker.app.account.persistence.repository.AccountEntityRepository;
import com.ExpenseTracker.app.account.presentation.dto.AccountDTO;
import com.ExpenseTracker.app.account.presentation.dto.CreateAccountDTO;
import com.ExpenseTracker.app.account.presentation.dto.UpdateAccountDTO;
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
public class AccountServiceImpl implements IAccountService {

    private final AccountEntityRepository accountRepository;
    private final UserEntityRepository userRepository;
    private final AccountMapper accountMapper;

    @Override
    public AccountDTO create(CreateAccountDTO dto, UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado con id: " + userId));
        AccountEntity entity = accountMapper.toEntity(dto);
        entity.setUser(user);
        return accountMapper.toDTO(accountRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountDTO> findAllByUser(UUID userId) {
        return accountRepository.findByUser_Id(userId).stream()
                .map(accountMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDTO findById(UUID id, UUID userId) {
        return accountRepository.findByIdAndUser_Id(id, userId)
                .map(accountMapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada con id: " + id));
    }

    @Override
    public AccountDTO update(UUID id, UpdateAccountDTO dto, UUID userId) {
        AccountEntity entity = accountRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada con id: " + id));
        accountMapper.updateEntityFromDTO(dto, entity);
        return accountMapper.toDTO(accountRepository.save(entity));
    }

    @Override
    public void delete(UUID id, UUID userId) {
        AccountEntity entity = accountRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada con id: " + id));
        accountRepository.delete(entity);
    }
}
