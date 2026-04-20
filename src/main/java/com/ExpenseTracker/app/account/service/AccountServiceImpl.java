package com.ExpenseTracker.app.account.service;

import com.ExpenseTracker.app.account.mapper.AccountMapper;
import com.ExpenseTracker.app.account.persistence.entity.AccountEntity;
import com.ExpenseTracker.app.account.persistence.repository.AccountEntityRepository;
import com.ExpenseTracker.app.account.presentation.dto.AccountDTO;
import com.ExpenseTracker.app.account.presentation.dto.AccountImpactDTO;
import com.ExpenseTracker.app.account.presentation.dto.CreateAccountDTO;
import com.ExpenseTracker.app.account.presentation.dto.UpdateAccountDTO;
import com.ExpenseTracker.app.transaction.persistence.repository.TransactionEntityRepository;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.util.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountServiceImpl implements IAccountService {

    private final AccountEntityRepository accountRepository;
    private final UserEntityRepository userRepository;
    private final TransactionEntityRepository transactionRepository;
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

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("balance", "name", "bank", "createdAt");

    @Override
    @Transactional(readOnly = true)
    public List<AccountDTO> findAllByUserFiltered(UUID userId, String search, String currency,
                                                   String sortBy, String sortDir) {
        String field = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(dir, field);

        String s = (search != null && !search.isBlank()) ? search.trim().toLowerCase() : "";
        String pattern = "%" + s + "%"; // "%%" matches every row
        String curr = (currency != null && !currency.isBlank()) ? currency : null;

        return accountRepository.findByUserFiltered(userId, pattern, curr, sort).stream()
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
        // Cascade soft-delete: transactions of this account go to trash too.
        transactionRepository.softDeleteByAccount(id);
        accountRepository.delete(entity); // triggers @SQLDelete → UPDATE deleted=true
    }

    @Override
    @Transactional(readOnly = true)
    public AccountImpactDTO impact(UUID id, UUID userId) {
        // Ensure ownership (404 if not theirs)
        accountRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada con id: " + id));
        return AccountImpactDTO.builder()
                .transactions(transactionRepository.countByAccount_Id(id))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountDTO> findTrashByUser(UUID userId) {
        return accountRepository.findDeletedByUser(userId).stream()
                .map(accountMapper::toDTO)
                .toList();
    }

    @Override
    public AccountDTO restore(UUID id, UUID userId) {
        AccountEntity entity = accountRepository.findByIdAndUserIncludingDeleted(id, userId)
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada con id: " + id));
        accountRepository.restoreByIdAndUser(id, userId);
        transactionRepository.restoreByAccount(id);
        entity.setDeleted(false);
        return accountMapper.toDTO(entity);
    }

    @Override
    public void deletePermanent(UUID id, UUID userId) {
        // Verify ownership (including trashed)
        accountRepository.findByIdAndUserIncludingDeleted(id, userId)
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada con id: " + id));
        // Hard-cascade: destroy transactions first to respect FK constraints
        transactionRepository.hardDeleteByAccount(id);
        accountRepository.hardDeleteByIdAndUser(id, userId);
    }
}
