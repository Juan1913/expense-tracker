package com.ExpenseTracker.app.debt.mapper;

import com.ExpenseTracker.app.debt.persistence.entity.DebtEntity;
import com.ExpenseTracker.app.debt.presentation.dto.CreateDebtDTO;
import com.ExpenseTracker.app.debt.presentation.dto.DebtDTO;
import com.ExpenseTracker.app.debt.presentation.dto.UpdateDebtDTO;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface DebtMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(target = "monthlyRate", ignore = true)
    @Mapping(target = "progressPercentage", ignore = true)
    @Mapping(target = "qualityBadge", ignore = true)
    @Mapping(target = "qualityHint", ignore = true)
    DebtDTO toDTO(DebtEntity entity);

    @AfterMapping
    default void enrich(@MappingTarget DebtDTO dto, DebtEntity entity) {
        // Tasa mensual equivalente: (1 + r_anual)^(1/12) − 1
        BigDecimal annual = entity.getAnnualRate();
        if (annual != null) {
            double r = annual.doubleValue();
            double monthly = Math.pow(1.0 + r, 1.0 / 12.0) - 1.0;
            dto.setMonthlyRate(BigDecimal.valueOf(monthly).setScale(8, RoundingMode.HALF_UP));
        } else {
            dto.setMonthlyRate(BigDecimal.ZERO);
        }

        // Progreso = 1 − saldo / principal (0..1) escalado a porcentaje 0..100
        BigDecimal principal = entity.getPrincipal();
        BigDecimal balance = entity.getCurrentBalance();
        if (principal != null && principal.compareTo(BigDecimal.ZERO) > 0 && balance != null) {
            BigDecimal pct = BigDecimal.ONE
                    .subtract(balance.divide(principal, 4, RoundingMode.HALF_UP))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            if (pct.compareTo(BigDecimal.ZERO) < 0) pct = BigDecimal.ZERO;
            if (pct.compareTo(BigDecimal.valueOf(100)) > 0) pct = BigDecimal.valueOf(100);
            dto.setProgressPercentage(pct);
        } else {
            dto.setProgressPercentage(BigDecimal.ZERO);
        }

        String[] q = com.ExpenseTracker.app.debt.service.DebtServiceImpl.qualityFor(entity.getAnnualRate());
        dto.setQualityBadge(q[0]);
        dto.setQualityHint(q[1]);
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "status", ignore = true)
    DebtEntity toEntity(CreateDebtDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    void updateEntityFromDTO(UpdateDebtDTO dto, @MappingTarget DebtEntity entity);
}
