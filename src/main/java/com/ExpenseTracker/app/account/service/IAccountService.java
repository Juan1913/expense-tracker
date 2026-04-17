package com.ExpenseTracker.app.account.service;

import com.ExpenseTracker.app.account.presentation.dto.AccountDTO;
import com.ExpenseTracker.app.account.presentation.dto.CreateAccountDTO;
import com.ExpenseTracker.app.account.presentation.dto.UpdateAccountDTO;

import java.util.List;
import java.util.UUID;

public interface IAccountService {

    AccountDTO create(CreateAccountDTO dto, UUID userId);

    List<AccountDTO> findAllByUser(UUID userId);

    AccountDTO findById(UUID id, UUID userId);

    AccountDTO update(UUID id, UpdateAccountDTO dto, UUID userId);

    void delete(UUID id, UUID userId);
}
