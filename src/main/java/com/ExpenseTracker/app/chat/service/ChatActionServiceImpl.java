package com.ExpenseTracker.app.chat.service;

import com.ExpenseTracker.app.account.persistence.entity.AccountEntity;
import com.ExpenseTracker.app.account.persistence.repository.AccountEntityRepository;
import com.ExpenseTracker.app.category.persistence.entity.CategoryEntity;
import com.ExpenseTracker.app.category.persistence.repository.CategoryEntityRepository;
import com.ExpenseTracker.app.chat.persistence.entity.PendingChatActionEntity;
import com.ExpenseTracker.app.chat.persistence.repository.PendingChatActionRepository;
import com.ExpenseTracker.app.chat.presentation.dto.PendingActionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.CreateTransactionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionDTO;
import com.ExpenseTracker.app.transaction.service.ITransactionService;
import com.ExpenseTracker.util.enums.ChatActionStatus;
import com.ExpenseTracker.util.enums.ChatActionType;
import com.ExpenseTracker.util.enums.TransactionType;
import com.ExpenseTracker.util.exception.NotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChatActionServiceImpl implements IChatActionService {

    private final PendingChatActionRepository repository;
    private final ITransactionService transactionService;
    private final AccountEntityRepository accountRepository;
    private final CategoryEntityRepository categoryRepository;
    private final ObjectMapper objectMapper;

    @Override
    public PendingActionDTO confirm(UUID actionId, UUID userId) {
        PendingChatActionEntity action = repository.findByIdAndUser_Id(actionId, userId)
                .orElseThrow(() -> new NotFoundException("Acción no encontrada"));

        if (action.getStatus() != ChatActionStatus.PENDING) {
            return toDTO(action);
        }

        Map<String, Object> payload = readJson(action.getPayloadJson());

        try {
            switch (action.getType()) {
                case CREATE_EXPENSE -> executeTransaction(userId, action, payload, TransactionType.EXPENSE);
                case CREATE_INCOME -> executeTransaction(userId, action, payload, TransactionType.INCOME);
                case CREATE_TRANSFER -> executeTransfer(userId, action, payload);
            }
        } catch (Exception e) {
            log.warn("Falló confirmación de acción {}: {}", actionId, e.getMessage());
            action.setStatus(ChatActionStatus.FAILED);
            action.setResultMessage(truncate(e.getMessage(), 500));
        }

        action.setResolvedAt(LocalDateTime.now());
        return toDTO(repository.save(action));
    }

    @Override
    public PendingActionDTO reject(UUID actionId, UUID userId) {
        PendingChatActionEntity action = repository.findByIdAndUser_Id(actionId, userId)
                .orElseThrow(() -> new NotFoundException("Acción no encontrada"));

        if (action.getStatus() == ChatActionStatus.PENDING) {
            action.setStatus(ChatActionStatus.REJECTED);
            action.setResolvedAt(LocalDateTime.now());
            action.setResultMessage("Cancelada por el usuario");
            repository.save(action);
        }
        return toDTO(action);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void executeTransaction(UUID userId, PendingChatActionEntity action,
                                    Map<String, Object> payload, TransactionType type) {
        BigDecimal amount = parseAmount(payload.get("amount"));
        AccountEntity account = findAccountByName(userId, str(payload.get("accountName")));
        CategoryEntity category = findCategoryByName(userId, str(payload.get("categoryName")), type);

        CreateTransactionDTO dto = CreateTransactionDTO.builder()
                .amount(amount)
                .date(LocalDateTime.now())
                .description(str(payload.get("description")))
                .type(type)
                .accountId(account.getId())
                .categoryId(category.getId())
                .build();

        TransactionDTO created = transactionService.create(dto, userId);
        action.setStatus(ChatActionStatus.CONFIRMED);
        action.setResultMessage("Creada con id " + created.getId());
    }

    private void executeTransfer(UUID userId, PendingChatActionEntity action, Map<String, Object> payload) {
        BigDecimal amount = parseAmount(payload.get("amount"));
        AccountEntity from = findAccountByName(userId, str(payload.get("fromAccountName")));
        AccountEntity to = findAccountByName(userId, str(payload.get("toAccountName")));

        if (from.getId().equals(to.getId())) {
            throw new IllegalArgumentException("Origen y destino deben ser distintos");
        }

        CreateTransactionDTO dto = CreateTransactionDTO.builder()
                .amount(amount)
                .date(LocalDateTime.now())
                .description(str(payload.get("description")))
                .type(TransactionType.TRANSFER)
                .accountId(from.getId())
                .transferToAccountId(to.getId())
                .build();

        TransactionDTO created = transactionService.create(dto, userId);
        action.setStatus(ChatActionStatus.CONFIRMED);
        action.setResultMessage("Transferencia creada con id " + created.getId());
    }

    private AccountEntity findAccountByName(UUID userId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nombre de cuenta vacío");
        }
        List<AccountEntity> accounts = accountRepository.findByUser_Id(userId);
        String needle = name.trim().toLowerCase();
        return accounts.stream()
                .filter(a -> a.getName() != null && a.getName().toLowerCase().contains(needle))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No encontré una cuenta llamada \"" + name + "\""));
    }

    private CategoryEntity findCategoryByName(UUID userId, String name, TransactionType type) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nombre de categoría vacío");
        }
        List<CategoryEntity> all = categoryRepository.findByUser_IdAndType(userId, type.name());
        String needle = name.trim().toLowerCase();
        return all.stream()
                .filter(c -> c.getName() != null && c.getName().toLowerCase().contains(needle))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No encontré categoría \"" + name + "\" del tipo " + type));
    }

    private static BigDecimal parseAmount(Object raw) {
        if (raw == null) throw new IllegalArgumentException("Monto vacío");
        String s = raw.toString().replace(",", "").replace("$", "").trim();
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Monto inválido: " + raw);
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private Map<String, Object> readJson(String s) {
        if (s == null || s.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(s, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private PendingActionDTO toDTO(PendingChatActionEntity e) {
        return new PendingActionDTO(
                e.getId(), e.getType(), e.getSummary(),
                readJson(e.getPayloadJson()),
                e.getStatus(), e.getResultMessage(),
                e.getCreatedAt(), e.getResolvedAt()
        );
    }
}
