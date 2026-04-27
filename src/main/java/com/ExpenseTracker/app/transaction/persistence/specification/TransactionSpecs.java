package com.ExpenseTracker.app.transaction.persistence.specification;

import com.ExpenseTracker.app.transaction.persistence.entity.TransactionEntity;
import com.ExpenseTracker.util.enums.TransactionType;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Composable {@link Specification}s for TransactionEntity.
 *
 * Each helper returns {@code cb.conjunction()} (always-true) when the filter
 * is absent, so the final WHERE only contains the active conditions.  No
 * {@code IS NULL OR ...} anti-patterns, no sentinel values, no native SQL,
 * and {@code @SQLRestriction} keeps working because everything runs through
 * the JPA criteria layer.
 */
public final class TransactionSpecs {

    private TransactionSpecs() {}

    public static Specification<TransactionEntity> forUser(UUID userId) {
        return (root, q, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<TransactionEntity> hasType(TransactionType type) {
        return (root, q, cb) -> type == null ? cb.conjunction() : cb.equal(root.get("type"), type);
    }

    public static Specification<TransactionEntity> fromAccount(UUID accountId) {
        return (root, q, cb) -> accountId == null
                ? cb.conjunction()
                : cb.equal(root.get("account").get("id"), accountId);
    }

    public static Specification<TransactionEntity> fromCategory(UUID categoryId) {
        return (root, q, cb) -> {
            if (categoryId == null) return cb.conjunction();
            // LEFT JOIN porque category es nullable (TRANSFER).
            return cb.equal(root.join("category", JoinType.LEFT).get("id"), categoryId);
        };
    }

    public static Specification<TransactionEntity> dateFrom(LocalDateTime from) {
        return (root, q, cb) -> from == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("date"), from);
    }

    public static Specification<TransactionEntity> dateBefore(LocalDateTime to) {
        return (root, q, cb) -> to == null
                ? cb.conjunction()
                : cb.lessThan(root.get("date"), to);
    }

    public static Specification<TransactionEntity> amountAtLeast(BigDecimal min) {
        return (root, q, cb) -> min == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("amount"), min);
    }

    public static Specification<TransactionEntity> amountAtMost(BigDecimal max) {
        return (root, q, cb) -> max == null
                ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.get("amount"), max);
    }

    /** Case-insensitive match against description, category name and account name. */
    public static Specification<TransactionEntity> searchText(String search) {
        return (root, q, cb) -> {
            if (search == null || search.isBlank()) return cb.conjunction();
            String pattern = "%" + search.trim().toLowerCase() + "%";
            // LEFT JOIN para category porque es nullable (TRANSFER no tiene categoría).
            Predicate desc = cb.like(
                    cb.lower(cb.coalesce(root.<String>get("description"), "")), pattern);
            Predicate cat  = cb.like(
                    cb.lower(cb.coalesce(root.join("category", JoinType.LEFT).<String>get("name"), "")),
                    pattern);
            Predicate acct = cb.like(cb.lower(root.get("account").<String>get("name")), pattern);
            return cb.or(desc, cat, acct);
        };
    }
}
