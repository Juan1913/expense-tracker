package com.ExpenseTracker.app.debt.persistence.repository;

import com.ExpenseTracker.app.debt.persistence.entity.DebtPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface DebtPaymentRepository extends JpaRepository<DebtPaymentEntity, UUID> {

    List<DebtPaymentEntity> findByDebt_IdOrderByPaymentDateDesc(UUID debtId);

    @Query("SELECT COALESCE(SUM(p.amountCapital), 0) FROM DebtPaymentEntity p WHERE p.debt.id = :debtId")
    BigDecimal sumCapitalByDebt(@Param("debtId") UUID debtId);

    @Query("SELECT COALESCE(SUM(p.amountInterest), 0) FROM DebtPaymentEntity p WHERE p.debt.id = :debtId")
    BigDecimal sumInterestByDebt(@Param("debtId") UUID debtId);
}
