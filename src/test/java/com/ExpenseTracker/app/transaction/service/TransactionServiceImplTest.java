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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock private TransactionEntityRepository transactionRepository;
    @Mock private AccountEntityRepository accountRepository;
    @Mock private CategoryEntityRepository categoryRepository;
    @Mock private UserEntityRepository userRepository;
    @Mock private TransactionMapper transactionMapper;

    @InjectMocks private TransactionServiceImpl service;

    private UUID userId;
    private UserEntity user;
    private AccountEntity sourceAccount;
    private AccountEntity targetAccount;
    private CategoryEntity category;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        user = UserEntity.builder().id(userId).build();
        sourceAccount = AccountEntity.builder()
                .id(UUID.randomUUID())
                .name("Corriente")
                .balance(new BigDecimal("100000"))
                .user(user)
                .build();
        targetAccount = AccountEntity.builder()
                .id(UUID.randomUUID())
                .name("Ahorro")
                .balance(new BigDecimal("0"))
                .user(user)
                .build();
        category = CategoryEntity.builder()
                .id(UUID.randomUUID())
                .name("Restaurantes")
                .build();

        // Defaults that most tests need; lenient porque algunos tests no usan todas las stubs.
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(transactionRepository.save(any(TransactionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(transactionMapper.toDTO(any())).thenReturn(new TransactionDTO());
        lenient().when(transactionMapper.toEntity(any())).thenAnswer(inv -> {
            CreateTransactionDTO dto = inv.getArgument(0);
            return TransactionEntity.builder()
                    .amount(dto.getAmount())
                    .date(dto.getDate())
                    .type(dto.getType())
                    .build();
        });
    }

    // ─── EXPENSE ─────────────────────────────────────────────────────────────

    @Test
    void create_expense_withSufficientBalance_decreasesBalance() {
        CreateTransactionDTO dto = expenseDto("30000");
        stubAccount(sourceAccount);
        stubCategory();

        service.create(dto, userId);

        assertThat(sourceAccount.getBalance()).isEqualByComparingTo("70000");
        verify(accountRepository).save(sourceAccount);
        verify(transactionRepository).save(any(TransactionEntity.class));
    }

    @Test
    void create_expense_withInsufficientBalance_throwsAndDoesNotSave() {
        CreateTransactionDTO dto = expenseDto("999999");
        stubAccount(sourceAccount);
        stubCategory();

        assertThatThrownBy(() -> service.create(dto, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Saldo insuficiente");

        assertThat(sourceAccount.getBalance()).isEqualByComparingTo("100000"); // sin tocar
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void create_expense_withZeroBalanceAccount_throws() {
        sourceAccount.setBalance(BigDecimal.ZERO);
        CreateTransactionDTO dto = expenseDto("1");
        stubAccount(sourceAccount);
        stubCategory();

        assertThatThrownBy(() -> service.create(dto, userId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── INCOME ──────────────────────────────────────────────────────────────

    @Test
    void create_income_increasesBalance_andNoBalanceCheck() {
        CreateTransactionDTO dto = incomeDto("500000");
        stubAccount(sourceAccount);
        stubCategory();

        service.create(dto, userId);

        assertThat(sourceAccount.getBalance()).isEqualByComparingTo("600000");
    }

    // ─── TRANSFER ────────────────────────────────────────────────────────────

    @Test
    void create_transfer_movesBalanceBetweenAccounts() {
        CreateTransactionDTO dto = transferDto("40000");
        stubAccount(sourceAccount);
        stubAccount(targetAccount);

        service.create(dto, userId);

        assertThat(sourceAccount.getBalance()).isEqualByComparingTo("60000");
        assertThat(targetAccount.getBalance()).isEqualByComparingTo("40000");
        verify(accountRepository).save(sourceAccount);
        verify(accountRepository).save(targetAccount);
    }

    @Test
    void create_transfer_withInsufficientBalance_throws() {
        CreateTransactionDTO dto = transferDto("500000");
        stubAccount(sourceAccount);
        stubAccount(targetAccount);

        assertThatThrownBy(() -> service.create(dto, userId))
                .isInstanceOf(IllegalArgumentException.class);

        // Ningún balance se modifica al fallar.
        assertThat(sourceAccount.getBalance()).isEqualByComparingTo("100000");
        assertThat(targetAccount.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void create_transfer_withSameAccountSourceAndTarget_throws() {
        stubAccount(sourceAccount);
        CreateTransactionDTO dto = CreateTransactionDTO.builder()
                .amount(new BigDecimal("100"))
                .date(LocalDateTime.now())
                .type(TransactionType.TRANSFER)
                .accountId(sourceAccount.getId())
                .transferToAccountId(sourceAccount.getId())
                .build();

        assertThatThrownBy(() -> service.create(dto, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distintas");
    }

    @Test
    void create_transfer_withoutToAccountId_throws() {
        stubAccount(sourceAccount);
        CreateTransactionDTO dto = CreateTransactionDTO.builder()
                .amount(new BigDecimal("100"))
                .date(LocalDateTime.now())
                .type(TransactionType.TRANSFER)
                .accountId(sourceAccount.getId())
                .build();

        assertThatThrownBy(() -> service.create(dto, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destino");
    }

    // ─── DELETE reverts balance ──────────────────────────────────────────────

    @Test
    void delete_expense_restoresBalance() {
        TransactionEntity entity = TransactionEntity.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("25000"))
                .type(TransactionType.EXPENSE)
                .account(sourceAccount)
                .build();
        when(transactionRepository.findByIdAndUser_Id(entity.getId(), userId))
                .thenReturn(Optional.of(entity));

        service.delete(entity.getId(), userId);

        assertThat(sourceAccount.getBalance()).isEqualByComparingTo("125000");
    }

    @Test
    void delete_transfer_restoresBothBalances() {
        sourceAccount.setBalance(new BigDecimal("60000"));
        targetAccount.setBalance(new BigDecimal("40000"));
        TransactionEntity entity = TransactionEntity.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("40000"))
                .type(TransactionType.TRANSFER)
                .account(sourceAccount)
                .transferToAccount(targetAccount)
                .build();
        when(transactionRepository.findByIdAndUser_Id(entity.getId(), userId))
                .thenReturn(Optional.of(entity));

        service.delete(entity.getId(), userId);

        assertThat(sourceAccount.getBalance()).isEqualByComparingTo("100000");
        assertThat(targetAccount.getBalance()).isEqualByComparingTo("0");
    }

    // ─── UPDATE: revert old + apply new ─────────────────────────────────────

    @Test
    void update_expense_changingAmount_revertOldThenApplyNew() {
        sourceAccount.setBalance(new BigDecimal("70000")); // post-EXPENSE de 30K
        TransactionEntity entity = TransactionEntity.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("30000"))
                .type(TransactionType.EXPENSE)
                .account(sourceAccount)
                .build();
        when(transactionRepository.findByIdAndUser_Id(entity.getId(), userId))
                .thenReturn(Optional.of(entity));

        UpdateTransactionDTO dto = new UpdateTransactionDTO();
        dto.setAmount(new BigDecimal("50000"));

        service.update(entity.getId(), dto, userId);

        // Revert: 70K + 30K = 100K. Apply nuevo 50K: 100K - 50K = 50K.
        assertThat(sourceAccount.getBalance()).isEqualByComparingTo("50000");
        assertThat(entity.getAmount()).isEqualByComparingTo("50000");
    }

    @Test
    void update_changingType_throws() {
        TransactionEntity entity = TransactionEntity.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("100"))
                .type(TransactionType.EXPENSE)
                .account(sourceAccount)
                .build();
        when(transactionRepository.findByIdAndUser_Id(entity.getId(), userId))
                .thenReturn(Optional.of(entity));

        UpdateTransactionDTO dto = new UpdateTransactionDTO();
        dto.setType(TransactionType.INCOME);

        assertThatThrownBy(() -> service.update(entity.getId(), dto, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void stubAccount(AccountEntity acc) {
        when(accountRepository.findByIdAndUser_Id(acc.getId(), userId))
                .thenReturn(Optional.of(acc));
    }

    private void stubCategory() {
        lenient().when(categoryRepository.findByIdAndUser_Id(category.getId(), userId))
                .thenReturn(Optional.of(category));
    }

    private CreateTransactionDTO expenseDto(String amount) {
        return CreateTransactionDTO.builder()
                .amount(new BigDecimal(amount))
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .accountId(sourceAccount.getId())
                .categoryId(category.getId())
                .build();
    }

    private CreateTransactionDTO incomeDto(String amount) {
        return CreateTransactionDTO.builder()
                .amount(new BigDecimal(amount))
                .date(LocalDateTime.now())
                .type(TransactionType.INCOME)
                .accountId(sourceAccount.getId())
                .categoryId(category.getId())
                .build();
    }

    private CreateTransactionDTO transferDto(String amount) {
        return CreateTransactionDTO.builder()
                .amount(new BigDecimal(amount))
                .date(LocalDateTime.now())
                .type(TransactionType.TRANSFER)
                .accountId(sourceAccount.getId())
                .transferToAccountId(targetAccount.getId())
                .build();
    }
}
