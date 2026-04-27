package com.ExpenseTracker.app.transaction.service;

import com.ExpenseTracker.app.account.persistence.entity.AccountEntity;
import com.ExpenseTracker.app.account.persistence.repository.AccountEntityRepository;
import com.ExpenseTracker.app.category.persistence.entity.CategoryEntity;
import com.ExpenseTracker.app.category.persistence.repository.CategoryEntityRepository;
import com.ExpenseTracker.app.transaction.mapper.TransactionMapper;
import com.ExpenseTracker.app.transaction.persistence.entity.TransactionEntity;
import com.ExpenseTracker.app.transaction.persistence.repository.TransactionEntityRepository;
import com.ExpenseTracker.app.transaction.persistence.specification.TransactionSpecs;
import com.ExpenseTracker.app.transaction.presentation.dto.CreateTransactionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionSummaryDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.UpdateTransactionDTO;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.util.enums.TransactionType;
import com.ExpenseTracker.util.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

    @PersistenceContext
    private EntityManager em;

    @Override
    public TransactionDTO create(CreateTransactionDTO dto, UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado con id: " + userId));
        AccountEntity account = accountRepository.findByIdAndUser_Id(dto.getAccountId(), userId)
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada con id: " + dto.getAccountId()));

        TransactionEntity entity = transactionMapper.toEntity(dto);
        entity.setUser(user);
        entity.setAccount(account);

        if (dto.getType() == TransactionType.TRANSFER) {
            if (dto.getTransferToAccountId() == null) {
                throw new IllegalArgumentException("La cuenta destino es obligatoria para una transferencia");
            }
            if (dto.getTransferToAccountId().equals(dto.getAccountId())) {
                throw new IllegalArgumentException("La cuenta origen y destino deben ser distintas");
            }
            AccountEntity toAccount = accountRepository.findByIdAndUser_Id(dto.getTransferToAccountId(), userId)
                    .orElseThrow(() -> new NotFoundException("Cuenta destino no encontrada con id: " + dto.getTransferToAccountId()));
            entity.setTransferToAccount(toAccount);
            entity.setCategory(null);
        } else {
            if (dto.getCategoryId() == null) {
                throw new IllegalArgumentException("La categoría es obligatoria para ingresos y gastos");
            }
            CategoryEntity category = categoryRepository.findByIdAndUser_Id(dto.getCategoryId(), userId)
                    .orElseThrow(() -> new NotFoundException("Categoría no encontrada con id: " + dto.getCategoryId()));
            entity.setCategory(category);
            entity.setTransferToAccount(null);
        }

        applyEffect(entity);
        return transactionMapper.toDTO(transactionRepository.save(entity));
    }

    /**
     * Aplica el movimiento sobre los balances. EXPENSE y TRANSFER (origen)
     * validan que haya saldo suficiente; si no, lanzan IllegalArgumentException.
     */
    private void applyEffect(TransactionEntity t) {
        BigDecimal amount = t.getAmount();
        AccountEntity acc = t.getAccount();
        switch (t.getType()) {
            case EXPENSE -> {
                requireSufficientBalance(acc, amount);
                acc.setBalance(safeBalance(acc).subtract(amount));
                accountRepository.save(acc);
            }
            case INCOME -> {
                acc.setBalance(safeBalance(acc).add(amount));
                accountRepository.save(acc);
            }
            case TRANSFER -> {
                AccountEntity to = t.getTransferToAccount();
                requireSufficientBalance(acc, amount);
                acc.setBalance(safeBalance(acc).subtract(amount));
                to.setBalance(safeBalance(to).add(amount));
                accountRepository.save(acc);
                accountRepository.save(to);
            }
        }
    }

    private void revertEffect(TransactionEntity t) {
        BigDecimal amount = t.getAmount();
        AccountEntity acc = t.getAccount();
        switch (t.getType()) {
            case EXPENSE -> {
                acc.setBalance(safeBalance(acc).add(amount));
                accountRepository.save(acc);
            }
            case INCOME -> {
                acc.setBalance(safeBalance(acc).subtract(amount));
                accountRepository.save(acc);
            }
            case TRANSFER -> {
                AccountEntity to = t.getTransferToAccount();
                acc.setBalance(safeBalance(acc).add(amount));
                to.setBalance(safeBalance(to).subtract(amount));
                accountRepository.save(acc);
                accountRepository.save(to);
            }
        }
    }

    private void requireSufficientBalance(AccountEntity acc, BigDecimal amount) {
        if (safeBalance(acc).compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Saldo insuficiente en \"" + acc.getName() + "\". Disponible: "
                            + safeBalance(acc) + ", requerido: " + amount + ".");
        }
    }

    private static BigDecimal safeBalance(AccountEntity a) {
        return a.getBalance() == null ? BigDecimal.ZERO : a.getBalance();
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

    /** Combines all filter specs into a single {@link Specification}. */
    private Specification<TransactionEntity> buildSpec(
            UUID userId,
            TransactionType type, UUID accountId, UUID categoryId,
            LocalDateTime fromDate, LocalDateTime toDate,
            BigDecimal minAmount, BigDecimal maxAmount,
            String search) {
        return Specification.where(TransactionSpecs.forUser(userId))
                .and(TransactionSpecs.hasType(type))
                .and(TransactionSpecs.fromAccount(accountId))
                .and(TransactionSpecs.fromCategory(categoryId))
                .and(TransactionSpecs.dateFrom(fromDate))
                .and(TransactionSpecs.dateBefore(toDate))
                .and(TransactionSpecs.amountAtLeast(minAmount))
                .and(TransactionSpecs.amountAtMost(maxAmount))
                .and(TransactionSpecs.searchText(search));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionDTO> findAllFiltered(
            UUID userId,
            TransactionType type, UUID accountId, UUID categoryId,
            LocalDateTime fromDate, LocalDateTime toDate,
            BigDecimal minAmount, BigDecimal maxAmount,
            String search,
            Pageable pageable) {
        Specification<TransactionEntity> spec = buildSpec(
                userId, type, accountId, categoryId,
                fromDate, toDate, minAmount, maxAmount, search);
        return transactionRepository.findAll(spec, pageable).map(transactionMapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionSummaryDTO aggregates(
            UUID userId,
            TransactionType type, UUID accountId, UUID categoryId,
            LocalDateTime fromDate, LocalDateTime toDate,
            BigDecimal minAmount, BigDecimal maxAmount,
            String search) {
        Specification<TransactionEntity> spec = buildSpec(
                userId, type, accountId, categoryId,
                fromDate, toDate, minAmount, maxAmount, search);

        // Same spec, aggregated: SELECT type, SUM(amount), COUNT(*) ... GROUP BY type
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<TransactionEntity> root = cq.from(TransactionEntity.class);
        cq.multiselect(
                root.get("type"),
                cb.coalesce(cb.sum(root.<BigDecimal>get("amount")), BigDecimal.ZERO),
                cb.count(root))
          .where(spec.toPredicate(root, cq, cb))
          .groupBy(root.get("type"));

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        long incomeCount = 0;
        long expenseCount = 0;
        for (Object[] row : em.createQuery(cq).getResultList()) {
            TransactionType t = (TransactionType) row[0];
            BigDecimal sum   = (BigDecimal) row[1];
            long count       = ((Number) row[2]).longValue();
            // TRANSFER no afecta ingresos ni gastos.
            if (t == TransactionType.INCOME)       { totalIncome = sum;  incomeCount = count; }
            else if (t == TransactionType.EXPENSE) { totalExpense = sum; expenseCount = count; }
        }
        return TransactionSummaryDTO.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netBalance(totalIncome.subtract(totalExpense))
                .incomeCount(incomeCount)
                .expenseCount(expenseCount)
                .totalCount(incomeCount + expenseCount)
                .build();
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

        if (dto.getType() != null && dto.getType() != entity.getType()) {
            throw new IllegalArgumentException(
                    "No se puede cambiar el tipo. Eliminá la transacción y creá una nueva.");
        }

        // Revertimos el efecto del estado anterior antes de aplicar los cambios.
        revertEffect(entity);

        if (dto.getAmount() != null) entity.setAmount(dto.getAmount());
        if (dto.getDate() != null) entity.setDate(dto.getDate());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());

        if (dto.getAccountId() != null) {
            entity.setAccount(accountRepository.findByIdAndUser_Id(dto.getAccountId(), userId)
                    .orElseThrow(() -> new NotFoundException("Cuenta no encontrada con id: " + dto.getAccountId())));
        }

        if (entity.getType() == TransactionType.TRANSFER) {
            if (dto.getTransferToAccountId() != null) {
                entity.setTransferToAccount(accountRepository.findByIdAndUser_Id(dto.getTransferToAccountId(), userId)
                        .orElseThrow(() -> new NotFoundException("Cuenta destino no encontrada con id: " + dto.getTransferToAccountId())));
            }
            if (entity.getAccount().getId().equals(entity.getTransferToAccount().getId())) {
                throw new IllegalArgumentException("La cuenta origen y destino deben ser distintas");
            }
            entity.setCategory(null);
        } else {
            if (dto.getCategoryId() != null) {
                entity.setCategory(categoryRepository.findByIdAndUser_Id(dto.getCategoryId(), userId)
                        .orElseThrow(() -> new NotFoundException("Categoría no encontrada con id: " + dto.getCategoryId())));
            }
        }

        applyEffect(entity);
        return transactionMapper.toDTO(transactionRepository.save(entity));
    }

    @Override
    public void delete(UUID id, UUID userId) {
        TransactionEntity entity = transactionRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Transacción no encontrada con id: " + id));
        revertEffect(entity);
        transactionRepository.delete(entity);
    }
}
