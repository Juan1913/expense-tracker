package com.ExpenseTracker.app.debt.persistence.entity;

import com.ExpenseTracker.app.account.persistence.entity.AccountEntity;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "debt_payments", indexes = {
        @Index(name = "idx_debt_payment_debt", columnList = "debt_id"),
        @Index(name = "idx_debt_payment_user", columnList = "user_id"),
})
public class DebtPaymentEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debt_id", nullable = false)
    private DebtEntity debt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "amount_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountTotal;

    @Column(name = "amount_interest", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountInterest;

    @Column(name = "amount_capital", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountCapital;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private AccountEntity account;

    @Column(name = "transaction_id", columnDefinition = "uuid")
    private UUID transactionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
