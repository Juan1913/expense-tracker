package com.ExpenseTracker.app.chat.service;

import com.ExpenseTracker.app.chat.presentation.dto.PendingActionDTO;

import java.util.UUID;

public interface IChatActionService {

    PendingActionDTO confirm(UUID actionId, UUID userId);

    PendingActionDTO reject(UUID actionId, UUID userId);
}
