package com.ExpenseTracker.app.debt.presentation.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyComparisonDTO {

    /** Pago mensual fijo total disponible (mínimos + extra). */
    private BigDecimal monthlyBudget;

    /** Suma de minimumPayment de todas las deudas activas. */
    private BigDecimal totalMinimum;

    /** Excedente sobre los mínimos que el usuario decidió aportar. */
    private BigDecimal extraBudget;

    private PayoffPlanDTO minimumOnly;
    private PayoffPlanDTO snowball;
    private PayoffPlanDTO avalanche;

    /** Mejor estrategia recomendada por menor interés total. */
    private String recommended;

    /** Ahorro de la recomendada vs minimumOnly (en intereses). */
    private BigDecimal interestSavedVsMinimum;

    /** Meses ahorrados de la recomendada vs minimumOnly. */
    private int monthsSavedVsMinimum;
}
