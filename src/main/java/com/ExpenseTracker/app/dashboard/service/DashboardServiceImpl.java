package com.ExpenseTracker.app.dashboard.service;

import com.ExpenseTracker.app.account.persistence.entity.AccountEntity;
import com.ExpenseTracker.app.account.persistence.repository.AccountEntityRepository;
import com.ExpenseTracker.app.budget.persistence.entity.BudgetEntity;
import com.ExpenseTracker.app.budget.persistence.repository.BudgetEntityRepository;
import com.ExpenseTracker.app.dashboard.presentation.dto.*;
import com.ExpenseTracker.app.transaction.persistence.entity.TransactionEntity;
import com.ExpenseTracker.app.transaction.persistence.repository.TransactionEntityRepository;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.util.enums.TransactionType;
import com.ExpenseTracker.util.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements IDashboardService {

    private final TransactionEntityRepository transactionRepository;
    private final BudgetEntityRepository budgetRepository;
    private final UserEntityRepository userRepository;
    private final AccountEntityRepository accountRepository;

    @Qualifier("dashboardExecutor")
    private final Executor dashboardExecutor;

    @Override
    public DashboardSummaryDTO getSummary(UUID userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sixMonthsAgo = now.minusMonths(6).withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfToday = now.toLocalDate().plusDays(1).atStartOfDay();
        LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime monthEnd = monthStart.plusMonths(1);

        CompletableFuture<Long> countFuture = CompletableFuture.supplyAsync(
                () -> transactionRepository.countByUser_Id(userId), dashboardExecutor);

        CompletableFuture<BigDecimal> incomeFuture = CompletableFuture.supplyAsync(
                () -> transactionRepository.sumAmountByUserIdAndType(userId, TransactionType.INCOME),
                dashboardExecutor);

        CompletableFuture<BigDecimal> expenseFuture = CompletableFuture.supplyAsync(
                () -> transactionRepository.sumAmountByUserIdAndType(userId, TransactionType.EXPENSE),
                dashboardExecutor);

        CompletableFuture<List<TransactionEntity>> monthlyFuture = CompletableFuture.supplyAsync(
                () -> transactionRepository.findByUser_IdAndDateBetweenOrderByDateAsc(userId, sixMonthsAgo, endOfToday),
                dashboardExecutor);

        CompletableFuture<List<Object[]>> categoryFuture = CompletableFuture.supplyAsync(
                () -> transactionRepository.findExpensesByCategoryForUser(userId), dashboardExecutor);

        CompletableFuture<List<BudgetEntity>> budgetFuture = CompletableFuture.supplyAsync(
                () -> budgetRepository.findByUser_IdAndMonthAndYear(userId, now.getMonthValue(), now.getYear()),
                dashboardExecutor);

        CompletableFuture<List<Object[]>> currentMonthCategoryFuture = CompletableFuture.supplyAsync(
                () -> transactionRepository.findExpensesByCategoryAndPeriod(userId, monthStart, monthEnd),
                dashboardExecutor);

        CompletableFuture<UserEntity> userFuture = CompletableFuture.supplyAsync(
                () -> userRepository.findById(userId)
                        .orElseThrow(() -> new NotFoundException("Usuario no encontrado")),
                dashboardExecutor);

        CompletableFuture<List<AccountEntity>> accountsFuture = CompletableFuture.supplyAsync(
                () -> accountRepository.findByUser_Id(userId), dashboardExecutor);

        CompletableFuture.allOf(countFuture, incomeFuture, expenseFuture,
                monthlyFuture, categoryFuture, budgetFuture, currentMonthCategoryFuture,
                userFuture, accountsFuture).join();

        BigDecimal totalIncome = incomeFuture.join();
        BigDecimal totalExpenses = expenseFuture.join();
        BigDecimal totalSavings = totalIncome.subtract(totalExpenses);

        // Patrimonio neto = Σ saldos de cuentas activas; separado por flag isSavings.
        List<AccountEntity> accounts = accountsFuture.join();
        BigDecimal totalInSavings = BigDecimal.ZERO;
        BigDecimal totalOperational = BigDecimal.ZERO;
        for (AccountEntity a : accounts) {
            BigDecimal bal = a.getBalance() == null ? BigDecimal.ZERO : a.getBalance();
            if (a.isSavings()) totalInSavings = totalInSavings.add(bal);
            else               totalOperational = totalOperational.add(bal);
        }
        BigDecimal totalNetWorth = totalOperational.add(totalInSavings);

        List<TransactionEntity> transactions = monthlyFuture.join();

        return DashboardSummaryDTO.builder()
                .totalTransactions(countFuture.join())
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .totalSavings(totalSavings)
                .totalNetWorth(totalNetWorth)
                .totalInSavingsAccounts(totalInSavings)
                .totalOperational(totalOperational)
                .monthlySummaries(buildMonthlySummaries(transactions))
                .expensesByCategory(buildCategoryExpenses(categoryFuture.join(), totalExpenses))
                .budgetComparisons(buildBudgetComparisons(budgetFuture.join(), currentMonthCategoryFuture.join()))
                .monthlySavingsProgress(buildMonthlySavingsProgress(transactions, userFuture.join()))
                .build();
    }

    private List<MonthlySummaryDTO> buildMonthlySummaries(List<TransactionEntity> transactions) {
        Map<String, BigDecimal[]> monthMap = new LinkedHashMap<>();
        for (TransactionEntity t : transactions) {
            // TRANSFER no es ni ingreso ni gasto: lo saltamos.
            if (t.getType() == TransactionType.TRANSFER) continue;
            String key = t.getDate().getYear() + "-" + t.getDate().getMonthValue();
            monthMap.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] totals = monthMap.get(key);
            if (t.getType() == TransactionType.INCOME) {
                totals[0] = totals[0].add(t.getAmount());
            } else {
                totals[1] = totals[1].add(t.getAmount());
            }
        }
        return monthMap.entrySet().stream().map(entry -> {
            String[] parts = entry.getKey().split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            BigDecimal income = entry.getValue()[0];
            BigDecimal expenses = entry.getValue()[1];
            return MonthlySummaryDTO.builder()
                    .year(year).month(month)
                    .monthName(Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault()))
                    .income(income).expenses(expenses).savings(income.subtract(expenses))
                    .build();
        }).collect(Collectors.toList());
    }

    private List<CategoryExpenseDTO> buildCategoryExpenses(List<Object[]> rows, BigDecimal totalExpenses) {
        if (rows.isEmpty() || totalExpenses.compareTo(BigDecimal.ZERO) == 0) return Collections.emptyList();
        return rows.stream().map(row -> {
            BigDecimal amount = (BigDecimal) row[1];
            BigDecimal percentage = amount.divide(totalExpenses, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            return CategoryExpenseDTO.builder()
                    .category((String) row[0]).amount(amount).percentage(percentage).build();
        }).collect(Collectors.toList());
    }

    private List<BudgetComparisonDTO> buildBudgetComparisons(List<BudgetEntity> budgets, List<Object[]> actualRows) {
        Map<UUID, BigDecimal> actualByCategory = new HashMap<>();
        Map<UUID, String> nameByCategory = new HashMap<>();
        for (Object[] row : actualRows) {
            UUID catId = (UUID) row[0];
            actualByCategory.put(catId, (BigDecimal) row[2]);
            nameByCategory.put(catId, (String) row[1]);
        }
        return budgets.stream().map(budget -> {
            UUID catId = budget.getCategory().getId();
            BigDecimal actual = actualByCategory.getOrDefault(catId, BigDecimal.ZERO);
            BigDecimal budgeted = budget.getAmount();
            BigDecimal percentage = budgeted.compareTo(BigDecimal.ZERO) > 0
                    ? actual.divide(budgeted, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return BudgetComparisonDTO.builder()
                    .categoryId(catId)
                    .category(budget.getCategory().getName())
                    .budgeted(budgeted).actual(actual).percentage(percentage)
                    .build();
        }).collect(Collectors.toList());
    }

    private List<MonthlySavingsProgressDTO> buildMonthlySavingsProgress(
            List<TransactionEntity> transactions, UserEntity user) {
        BigDecimal goal = user.getMonthlySavingsGoal();
        Map<String, BigDecimal[]> monthMap = new LinkedHashMap<>();
        for (TransactionEntity t : transactions) {
            if (t.getType() == TransactionType.TRANSFER) continue;
            String key = t.getDate().getYear() + "-" + t.getDate().getMonthValue();
            monthMap.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] totals = monthMap.get(key);
            if (t.getType() == TransactionType.INCOME) {
                totals[0] = totals[0].add(t.getAmount());
            } else {
                totals[1] = totals[1].add(t.getAmount());
            }
        }
        return monthMap.entrySet().stream().map(entry -> {
            String[] parts = entry.getKey().split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            BigDecimal savings = entry.getValue()[0].subtract(entry.getValue()[1]);
            BigDecimal progress = BigDecimal.ZERO;
            if (goal != null && goal.compareTo(BigDecimal.ZERO) > 0) {
                progress = savings.divide(goal, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            }
            return MonthlySavingsProgressDTO.builder()
                    .year(year).month(month)
                    .monthName(Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault()))
                    .savingsAmount(savings).savingsGoal(goal).progressPercentage(progress)
                    .build();
        }).collect(Collectors.toList());
    }
}
