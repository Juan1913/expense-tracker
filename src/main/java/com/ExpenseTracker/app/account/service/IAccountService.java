package com.ExpenseTracker.app.account.service;

import com.ExpenseTracker.app.account.presentation.dto.AccountDTO;
import com.ExpenseTracker.app.account.presentation.dto.AccountImpactDTO;
import com.ExpenseTracker.app.account.presentation.dto.CreateAccountDTO;
import com.ExpenseTracker.app.account.presentation.dto.UpdateAccountDTO;

import java.util.List;
import java.util.UUID;

public interface IAccountService {

    AccountDTO create(CreateAccountDTO dto, UUID userId);

    List<AccountDTO> findAllByUser(UUID userId);

    /** Filtered account listing with search + currency + sort. */
    List<AccountDTO> findAllByUserFiltered(UUID userId, String search, String currency,
                                           String sortBy, String sortDir);

    AccountDTO findById(UUID id, UUID userId);

    AccountDTO update(UUID id, UpdateAccountDTO dto, UUID userId);

    /** Soft-delete with cascade: account + dependent transactions. */
    void delete(UUID id, UUID userId);

    /** Preview of what a delete would affect. */
    AccountImpactDTO impact(UUID id, UUID userId);

    /** List all soft-deleted accounts for the given user (trash view). */
    List<AccountDTO> findTrashByUser(UUID userId);

    /** Un-delete a soft-deleted account + cascade restore transactions. */
    AccountDTO restore(UUID id, UUID userId);

    /** Physical hard-delete with cascade. Irreversible. */
    void deletePermanent(UUID id, UUID userId);
}
