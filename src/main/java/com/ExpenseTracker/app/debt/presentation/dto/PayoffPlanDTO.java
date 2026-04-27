package com.ExpenseTracker.app.debt.presentation.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Resultado de una estrategia de pago aplicada al portafolio de deudas del
 * usuario: meses hasta liquidar, intereses totales y orden de pago.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoffPlanDTO {

    /** "MINIMUM_ONLY" | "SNOWBALL" | "AVALANCHE". */
    private String strategy;

    /** Pago mensual total = Σ minPayments + extraBudget. */
    private BigDecimal monthlyTotal;

    /** Meses hasta que todas las deudas estén en 0. */
    private int monthsToFreedom;

    /** Suma de capital + intereses pagados durante el plan. */
    private BigDecimal totalPaid;

    /** Suma de intereses pagados (parte del costo). */
    private BigDecimal totalInterest;

    /** Orden en que se liquida cada deuda y mes en que cae. */
    private List<DebtPayoffOrder> order;

    /** Trayectoria mensual del saldo total agregado, para graficar. */
    private List<MonthlyBalancePoint> trajectory;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DebtPayoffOrder {
        private UUID debtId;
        private String name;
        private int payoffMonth;            // mes (1-indexado) en que llega a 0
        private BigDecimal interestPaid;    // intereses específicos de esa deuda
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MonthlyBalancePoint {
        private int month;                  // 0 = hoy, 1 = mes 1, ...
        private BigDecimal balance;         // saldo total agregado al final del mes
        private BigDecimal interestThisMonth;
    }
}
