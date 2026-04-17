package com.ExpenseTracker.infrastructure.ai.rag;

import com.ExpenseTracker.app.account.persistence.repository.AccountEntityRepository;
import com.ExpenseTracker.app.budget.persistence.repository.BudgetEntityRepository;
import com.ExpenseTracker.app.transaction.persistence.repository.TransactionEntityRepository;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.app.wishlist.persistence.repository.WishlistEntityRepository;
import com.ExpenseTracker.util.enums.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FinancialDocumentBuilder {

    private final UserEntityRepository userRepository;
    private final AccountEntityRepository accountRepository;
    private final TransactionEntityRepository transactionRepository;
    private final BudgetEntityRepository budgetRepository;
    private final WishlistEntityRepository wishlistRepository;

    public List<Document> buildDocuments(UUID userId) {
        List<Document> docs = new ArrayList<>();
        Map<String, Object> meta = Map.of("userId", userId.toString());

        buildUserDoc(userId, docs, meta);
        buildAccountDocs(userId, docs, meta);
        buildTransactionDocs(userId, docs, meta);
        buildBudgetDocs(userId, docs, meta);
        buildWishlistDocs(userId, docs, meta);

        return docs;
    }

    private void buildUserDoc(UUID userId, List<Document> docs, Map<String, Object> meta) {
        userRepository.findById(userId).ifPresent(user -> {
            String content = "Usuario: %s. Email: %s.".formatted(user.getUsername(), user.getEmail());
            if (user.getMonthlySavingsGoal() != null) {
                content += " Meta de ahorro mensual: $%s COP.".formatted(user.getMonthlySavingsGoal());
            }
            docs.add(new Document("user-goal-" + userId, content, meta));
        });
    }

    private void buildAccountDocs(UUID userId, List<Document> docs, Map<String, Object> meta) {
        accountRepository.findByUser_Id(userId).forEach(account -> {
            String content = "Cuenta '%s': saldo actual $%s %s.".formatted(
                    account.getName(), account.getBalance(), account.getCurrency());
            docs.add(new Document("account-" + account.getId(), content, meta));
        });
    }

    private void buildTransactionDocs(UUID userId, List<Document> docs, Map<String, Object> meta) {
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
        transactionRepository
                .findByUser_IdAndDateBetweenOrderByDateAsc(userId, threeMonthsAgo, LocalDateTime.now())
                .forEach(t -> {
                    String tipo = t.getType() == TransactionType.INCOME ? "INGRESO" : "GASTO";
                    String content = "%s de $%s en '%s' el %s desde cuenta '%s'%s.".formatted(
                            tipo,
                            t.getAmount(),
                            t.getCategory().getName(),
                            t.getDate().toLocalDate(),
                            t.getAccount().getName(),
                            t.getDescription() != null ? " — " + t.getDescription() : "");
                    docs.add(new Document("tx-" + t.getId(), content, meta));
                });
    }

    private void buildBudgetDocs(UUID userId, List<Document> docs, Map<String, Object> meta) {
        LocalDate now = LocalDate.now();
        budgetRepository.findByUser_IdAndMonthAndYear(userId, now.getMonthValue(), now.getYear())
                .forEach(b -> {
                    String mes = Month.of(b.getMonth()).getDisplayName(TextStyle.FULL, Locale.of("es"));
                    String content = "Presupuesto '%s' para %s %d: $%s asignado.".formatted(
                            b.getCategory().getName(), mes, b.getYear(), b.getAmount());
                    docs.add(new Document("budget-" + b.getId(), content, meta));
                });
    }

    private void buildWishlistDocs(UUID userId, List<Document> docs, Map<String, Object> meta) {
        wishlistRepository.findByUser_IdOrderByCreatedAtDesc(userId).forEach(w -> {
            String content = "Meta de ahorro '%s': objetivo $%s, ahorrado $%s (%s%%)%s. Estado: %s.".formatted(
                    w.getName(),
                    w.getTargetAmount(),
                    w.getCurrentAmount(),
                    w.getTargetAmount().compareTo(java.math.BigDecimal.ZERO) > 0
                            ? w.getCurrentAmount().multiply(java.math.BigDecimal.valueOf(100))
                                    .divide(w.getTargetAmount(), 0, java.math.RoundingMode.HALF_UP)
                            : 0,
                    w.getDeadline() != null ? ", fecha límite: " + w.getDeadline() : "",
                    w.getStatus());
            docs.add(new Document("wish-" + w.getId(), content, meta));
        });
    }
}
