package com.ExpenseTracker.app.debt.persistence.entity;

import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.util.enums.DebtStatus;
import com.ExpenseTracker.util.persistence.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

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
@Table(name = "debts")
@SQLDelete(sql = "UPDATE debts SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
public class DebtEntity extends SoftDeletableEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    /** Banco / acreedor (opcional). */
    @Column(length = 100)
    private String creditor;

    /** Monto original prestado. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal principal;

    /** Saldo restante a pagar. */
    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentBalance;

    /** Tasa de interés anual efectiva como fracción (0.18 = 18%). */
    @Column(name = "annual_rate", nullable = false, precision = 10, scale = 6)
    private BigDecimal annualRate;

    /** Pago mínimo mensual. */
    @Column(name = "minimum_payment", nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumPayment;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DebtStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = DebtStatus.ACTIVE;
        if (this.currentBalance == null) this.currentBalance = this.principal;
    }
}
