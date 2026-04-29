package com.ExpenseTracker.infrastructure.ai.tools;

import com.ExpenseTracker.app.account.presentation.dto.AccountDTO;
import com.ExpenseTracker.app.account.service.IAccountService;
import com.ExpenseTracker.app.debt.presentation.dto.DebtDTO;
import com.ExpenseTracker.app.debt.presentation.dto.StrategyComparisonDTO;
import com.ExpenseTracker.app.debt.service.IDebtService;
import com.ExpenseTracker.app.document.persistence.repository.UserDocumentRepository;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionSummaryDTO;
import com.ExpenseTracker.app.transaction.service.ITransactionService;
import com.ExpenseTracker.app.wishlist.presentation.dto.WishlistDTO;
import com.ExpenseTracker.app.wishlist.service.IWishlistService;
import com.ExpenseTracker.infrastructure.security.SecurityUtils;
import com.ExpenseTracker.util.enums.ChatActionType;
import com.ExpenseTracker.util.enums.DebtStatus;
import com.ExpenseTracker.util.enums.TransactionType;
import com.ExpenseTracker.util.enums.WishlistStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Funciones que el LLM puede invocar para responder con datos reales en vez
 * de RAG aproximado. El userId siempre se resuelve desde el SecurityContext —
 * el modelo nunca lo recibe ni lo elige.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FinBotTools {

    private final SecurityUtils securityUtils;
    private final IAccountService accountService;
    private final ITransactionService transactionService;
    private final IDebtService debtService;
    private final IWishlistService wishlistService;
    private final UserDocumentRepository documentRepository;
    private final VectorStore vectorStore;
    private final PendingActionCollector actionCollector;

    // ─── Cuentas / patrimonio ────────────────────────────────────────────────

    @Tool(name = "getAccountBalances",
          description = "Lista TODAS las cuentas del usuario con su saldo actual, banco y si están marcadas como ahorro. " +
                        "Úsalo para responder preguntas sobre cuánto tiene, dónde está su plata, o cuánto tiene ahorrado.")
    public List<Map<String, Object>> getAccountBalances() {
        UUID userId = securityUtils.getCurrentUserId();
        return accountService.findAllByUser(userId).stream()
                .map(this::accountAsMap)
                .toList();
    }

    @Tool(name = "getNetWorthSummary",
          description = "Patrimonio total: suma de saldos en cuentas operativas y de ahorro, menos deuda de tarjetas. " +
                        "Úsalo para responder '¿cuánto tengo en total?', '¿cuánto tengo ahorrado?' o '¿cuánto debo en tarjetas?'")
    public Map<String, Object> getNetWorthSummary() {
        UUID userId = securityUtils.getCurrentUserId();
        var accounts = accountService.findAllByUser(userId);
        BigDecimal savings = BigDecimal.ZERO;
        BigDecimal operational = BigDecimal.ZERO;
        BigDecimal cardDebt = BigDecimal.ZERO;
        for (AccountDTO a : accounts) {
            BigDecimal bal = a.getBalance() == null ? BigDecimal.ZERO : a.getBalance();
            if (a.isCreditCard())   cardDebt = cardDebt.add(bal);
            else if (a.isSavings()) savings = savings.add(bal);
            else                    operational = operational.add(bal);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("totalNetWorth", operational.add(savings).subtract(cardDebt));
        out.put("operationalBalance", operational);
        out.put("savingsBalance", savings);
        out.put("creditCardDebt", cardDebt);
        out.put("accountCount", accounts.size());
        return out;
    }

    // ─── Transacciones 

    @Tool(name = "searchTransactions",
          description = "Busca transacciones del usuario con filtros opcionales. " +
                        "Devuelve hasta 50 movimientos ordenados por fecha descendente con descripción, monto, tipo, " +
                        "categoría, cuenta y fecha. Si pides un período, también incluye los totales agregados.")
    public Map<String, Object> searchTransactions(
            @ToolParam(required = false, description = "Tipo: INCOME, EXPENSE o TRANSFER. Omitir para todos.")
            String type,
            @ToolParam(required = false, description = "Texto a buscar en descripción/categoría/cuenta.")
            String search,
            @ToolParam(required = false, description = "Fecha desde, formato YYYY-MM-DD.")
            String fromDate,
            @ToolParam(required = false, description = "Fecha hasta, formato YYYY-MM-DD.")
            String toDate,
            @ToolParam(required = false, description = "Monto mínimo.")
            String minAmount,
            @ToolParam(required = false, description = "Monto máximo.")
            String maxAmount
    ) {
        UUID userId = securityUtils.getCurrentUserId();
        TransactionType t = parseEnum(type, TransactionType.class);
        LocalDateTime from = parseDayStart(fromDate);
        LocalDateTime to = parseDayEnd(toDate);
        BigDecimal min = parseDecimal(minAmount);
        BigDecimal max = parseDecimal(maxAmount);

        var page = transactionService.findAllFiltered(
                userId, t, null, null, from, to, min, max, search,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "date"))
        );
        TransactionSummaryDTO summary = transactionService.aggregates(
                userId, t, null, null, from, to, min, max, search
        );

        Map<String, Object> out = new HashMap<>();
        out.put("totalIncome", summary.getTotalIncome());
        out.put("totalExpense", summary.getTotalExpense());
        out.put("netBalance", summary.getNetBalance());
        out.put("count", summary.getTotalCount());
        out.put("transactions", page.getContent().stream()
                .map(this::txAsMap)
                .toList());
        return out;
    }

    @Tool(name = "getMonthlySummary",
          description = "Resumen exacto de ingresos, gastos y ahorro neto para un mes específico. " +
                        "Excluye TRANSFER. Úsalo para preguntas tipo '¿cuánto gasté en marzo?'")
    public Map<String, Object> getMonthlySummary(
            @ToolParam(description = "Año, ej. 2026.") int year,
            @ToolParam(description = "Mes 1-12.") int month
    ) {
        UUID userId = securityUtils.getCurrentUserId();
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay();
        TransactionSummaryDTO summary = transactionService.aggregates(
                userId, null, null, null, from, to, null, null, null
        );
        Map<String, Object> out = new HashMap<>();
        out.put("year", year);
        out.put("month", month);
        out.put("monthName", ym.getMonth().toString());
        out.put("totalIncome", summary.getTotalIncome());
        out.put("totalExpense", summary.getTotalExpense());
        out.put("netSavings", summary.getNetBalance());
        out.put("transactionCount", summary.getTotalCount());
        return out;
    }

    @Tool(name = "getCategorySpending",
          description = "Cuánto gastó el usuario en una categoría específica durante los últimos N meses. " +
                        "Úsalo para preguntas tipo '¿cuánto gasté en restaurantes los últimos 3 meses?'")
    public Map<String, Object> getCategorySpending(
            @ToolParam(description = "Nombre exacto o parcial de la categoría.") String categoryName,
            @ToolParam(description = "Cantidad de meses hacia atrás desde hoy. 1 = mes actual, 3 = últimos 3 meses.")
            int monthsBack
    ) {
        UUID userId = securityUtils.getCurrentUserId();
        LocalDateTime from = LocalDate.now().minusMonths(monthsBack).atStartOfDay();
        TransactionSummaryDTO summary = transactionService.aggregates(
                userId, TransactionType.EXPENSE, null, null, from, null, null, null, categoryName
        );
        Map<String, Object> out = new HashMap<>();
        out.put("categorySearched", categoryName);
        out.put("monthsBack", monthsBack);
        out.put("totalSpent", summary.getTotalExpense());
        out.put("transactionCount", summary.getExpenseCount());
        out.put("avgPerTransaction", summary.getExpenseCount() > 0
                ? summary.getTotalExpense().divide(BigDecimal.valueOf(summary.getExpenseCount()), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        return out;
    }

    // Deudas 

    @Tool(name = "getActiveDebts",
          description = "Lista las deudas activas del usuario con saldo pendiente, tasa de interés anual y pago mínimo mensual. " +
                        "Úsalo para responder '¿cuánto debo?' o '¿qué deudas tengo?'")
    public List<Map<String, Object>> getActiveDebts() {
        UUID userId = securityUtils.getCurrentUserId();
        return debtService.findAllByUser(userId, DebtStatus.ACTIVE).stream()
                .map(this::debtAsMap)
                .toList();
    }

    @Tool(name = "analyzeDebtQuality",
          description = "Analiza una deuda específica del usuario: tasa, % capital pagado, intereses pagados, intereses estimados del próximo mes, calidad ('GOOD' / 'MEDIUM' / 'BAD' según tasa) y razonamiento. " +
                        "Úsalo para preguntas como '¿es buena o mala mi deuda X?', '¿cuánto pago de intereses?', '¿cuánto he pagado de capital?'")
    public Map<String, Object> analyzeDebtQuality(
            @ToolParam(description = "Nombre o parte del nombre de la deuda a analizar (ej. 'libre inversión', 'auto', 'hipoteca').")
            String debtName
    ) {
        UUID userId = securityUtils.getCurrentUserId();
        var debts = debtService.findAllByUser(userId, null);
        if (debts.isEmpty()) {
            return Map.of("error", "No tenés deudas estructuradas registradas.");
        }
        String norm = debtName == null ? "" : debtName.toLowerCase().trim();
        var match = debts.stream()
                .filter(d -> norm.isEmpty() || d.getName().toLowerCase().contains(norm)
                        || (d.getCreditor() != null && d.getCreditor().toLowerCase().contains(norm)))
                .findFirst()
                .orElse(debts.get(0));

        var summary = debtService.summary(match.getId(), userId);

        Map<String, Object> out = new HashMap<>();
        out.put("debtName", match.getName());
        out.put("creditor", match.getCreditor());
        out.put("annualRatePct", match.getAnnualRate() != null
                ? match.getAnnualRate().multiply(java.math.BigDecimal.valueOf(100)).setScale(2, java.math.RoundingMode.HALF_UP)
                : null);
        out.put("currentBalance", match.getCurrentBalance());
        out.put("principal", match.getPrincipal());
        out.put("totalCapitalPaid", summary.getTotalCapitalPaid());
        out.put("totalInterestPaid", summary.getTotalInterestPaid());
        out.put("capitalProgressPct", summary.getCapitalProgressPercentage());
        out.put("nextMonthInterestEstimate", summary.getNextMonthInterestEstimate());
        out.put("paymentsCount", summary.getPaymentsCount());
        out.put("qualityBadge", summary.getQualityBadge());
        out.put("qualityHint", summary.getQualityHint());
        return out;
    }

    @Tool(name = "compareDebtPayoffStrategies",
          description = "Compara las tres estrategias de pago de deudas (solo mínimos, snowball, avalanche) " +
                        "asumiendo que el usuario aporta un monto extra mensual sobre los mínimos. " +
                        "Devuelve meses hasta liquidar e intereses totales para cada una y recomienda la mejor.")
    public Map<String, Object> compareDebtPayoffStrategies(
            @ToolParam(description = "Monto extra mensual sobre los mínimos. 0 si solo paga mínimos.")
            String extraMonthlyBudget
    ) {
        UUID userId = securityUtils.getCurrentUserId();
        BigDecimal extra = parseDecimal(extraMonthlyBudget);
        StrategyComparisonDTO comp = debtService.compareStrategies(userId, extra);
        Map<String, Object> out = new HashMap<>();
        out.put("monthlyBudget", comp.getMonthlyBudget());
        out.put("totalMinimum", comp.getTotalMinimum());
        out.put("recommended", comp.getRecommended());
        out.put("interestSavedVsMinimum", comp.getInterestSavedVsMinimum());
        out.put("monthsSavedVsMinimum", comp.getMonthsSavedVsMinimum());
        out.put("minimumOnly", planSummary(comp.getMinimumOnly()));
        out.put("snowball", planSummary(comp.getSnowball()));
        out.put("avalanche", planSummary(comp.getAvalanche()));
        return out;
    }

    @Tool(name = "creditCardOverview",
          description = "Resumen de tarjetas de crédito: saldo actual (deuda), cupo disponible, % de uso y tasa anual. " +
                        "Úsalo para preguntas tipo '¿cuánto debo en tarjetas?' o '¿cuál tarjeta tiene más cupo libre?'")
    public Map<String, Object> creditCardOverview() {
        UUID userId = securityUtils.getCurrentUserId();
        var accounts = accountService.findAllByUser(userId).stream()
                .filter(AccountDTO::isCreditCard)
                .toList();
        BigDecimal totalDebt = BigDecimal.ZERO;
        BigDecimal totalLimit = BigDecimal.ZERO;
        List<Map<String, Object>> cards = new java.util.ArrayList<>();
        for (AccountDTO a : accounts) {
            BigDecimal bal = a.getBalance() == null ? BigDecimal.ZERO : a.getBalance();
            BigDecimal limit = a.getCreditLimit();
            BigDecimal rate = a.getAnnualRate();
            totalDebt = totalDebt.add(bal);
            if (limit != null) totalLimit = totalLimit.add(limit);
            Map<String, Object> m = new HashMap<>();
            m.put("name", a.getName());
            m.put("debt", bal);
            m.put("creditLimit", limit);
            m.put("annualRate", rate);
            if (limit != null && limit.signum() > 0) {
                m.put("usagePct", bal.multiply(BigDecimal.valueOf(100))
                        .divide(limit, 2, java.math.RoundingMode.HALF_UP));
                m.put("availableCredit", limit.subtract(bal).max(BigDecimal.ZERO));
            }
            cards.add(m);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("totalDebt", totalDebt);
        out.put("totalLimit", totalLimit);
        out.put("cardCount", accounts.size());
        out.put("cards", cards);
        return out;
    }

    @Tool(name = "recommendDebtPayoffPlan",
          description = "Recomienda una estrategia para liquidar deudas (préstamos + tarjetas) basada en datos reales. " +
                        "Considera ingresos, gastos y deudas. Devuelve la mejor estrategia, ahorro estimado y plan mensual.")
    public Map<String, Object> recommendDebtPayoffPlan(
            @ToolParam(required = false, description = "Monto extra mensual disponible para pagar deudas. Si es null, se calcula como ingresos - gastos del último mes.")
            String extraMonthlyBudget
    ) {
        UUID userId = securityUtils.getCurrentUserId();

        BigDecimal extra = parseDecimal(extraMonthlyBudget);
        if (extra == null) {
            YearMonth lastMonth = YearMonth.now().minusMonths(1);
            LocalDateTime from = lastMonth.atDay(1).atStartOfDay();
            LocalDateTime to = lastMonth.plusMonths(1).atDay(1).atStartOfDay();
            TransactionSummaryDTO summary = transactionService.aggregates(
                    userId, null, null, null, from, to, null, null, null);
            extra = summary.getNetBalance().max(BigDecimal.ZERO);
        }

        StrategyComparisonDTO comp = debtService.compareStrategies(userId, extra);

        var creditCards = accountService.findAllByUser(userId).stream()
                .filter(AccountDTO::isCreditCard)
                .toList();
        BigDecimal cardDebt = creditCards.stream()
                .map(a -> a.getBalance() == null ? BigDecimal.ZERO : a.getBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> out = new HashMap<>();
        out.put("availableExtraMonthly", extra);
        out.put("structuredDebtCount", debtService.findAllByUser(userId, DebtStatus.ACTIVE).size());
        out.put("creditCardDebt", cardDebt);
        out.put("creditCardCount", creditCards.size());
        out.put("recommendedStrategy", comp.getRecommended());
        out.put("monthsSavedVsMinimumOnly", comp.getMonthsSavedVsMinimum());
        out.put("interestSavedVsMinimumOnly", comp.getInterestSavedVsMinimum());
        out.put("minimumOnly", planSummary(comp.getMinimumOnly()));
        out.put("snowball", planSummary(comp.getSnowball()));
        out.put("avalanche", planSummary(comp.getAvalanche()));
        return out;
    }

    @Tool(name = "simulateRedirectingExpense",
          description = "Simula liberarte antes de las deudas si redirigís parte de un gasto recurrente al pago. " +
                        "Compara meses para liquidar y intereses pagados con vs sin el redirect.")
    public Map<String, Object> simulateRedirectingExpense(
            @ToolParam(description = "Monto mensual a redirigir hacia las deudas, en COP.")
            String monthlyAmountToRedirect,
            @ToolParam(required = false, description = "Categoría desde la que se redirigiría el gasto (sólo informativo, no afecta cálculos).")
            String fromCategoryName
    ) {
        UUID userId = securityUtils.getCurrentUserId();
        BigDecimal redirect = parseDecimal(monthlyAmountToRedirect);
        if (redirect == null || redirect.signum() <= 0) {
            return Map.of("error", "El monto a redirigir debe ser positivo.");
        }

        StrategyComparisonDTO before = debtService.compareStrategies(userId, BigDecimal.ZERO);
        StrategyComparisonDTO after = debtService.compareStrategies(userId, redirect);

        int monthsBefore = bestMonths(before);
        int monthsAfter = bestMonths(after);
        BigDecimal interestBefore = bestInterest(before);
        BigDecimal interestAfter = bestInterest(after);

        Map<String, Object> out = new HashMap<>();
        out.put("redirectedAmount", redirect);
        out.put("fromCategory", fromCategoryName);
        out.put("monthsToFreedomBefore", monthsBefore);
        out.put("monthsToFreedomAfter", monthsAfter);
        out.put("monthsSaved", monthsBefore < 0 || monthsAfter < 0 ? null : Math.max(0, monthsBefore - monthsAfter));
        out.put("interestPaidBefore", interestBefore);
        out.put("interestPaidAfter", interestAfter);
        out.put("interestSaved", interestBefore.subtract(interestAfter).max(BigDecimal.ZERO));
        return out;
    }

    @Tool(name = "analyzeProspectiveDebt",
          description = "Analiza una deuda hipotética (préstamo nuevo) antes de aceptarla: cuota mensual, total a pagar, intereses totales " +
                        "y porcentaje de tu ingreso mensual que representaría. Útil para decidir si tomar un crédito.")
    public Map<String, Object> analyzeProspectiveDebt(
            @ToolParam(description = "Monto del préstamo en COP.")
            String principal,
            @ToolParam(description = "Tasa anual efectiva en porcentaje. Ej: 24.5 para 24.5% E.A.")
            String annualRatePct,
            @ToolParam(description = "Plazo en meses, sólo el número.")
            String termMonths
    ) {
        BigDecimal p = parseDecimal(principal);
        BigDecimal annualPct = parseDecimal(annualRatePct);
        int term = parseInt(termMonths);
        if (p == null || p.signum() <= 0 || annualPct == null || term <= 0) {
            return Map.of("error", "Parámetros inválidos. Necesito principal positivo, tasa anual % y plazo en meses > 0.");
        }
        double annualRate = annualPct.doubleValue() / 100.0;
        double monthlyRate = Math.pow(1 + annualRate, 1.0 / 12.0) - 1;
        double pv = p.doubleValue();
        double monthlyPayment;
        if (monthlyRate == 0) {
            monthlyPayment = pv / term;
        } else {
            monthlyPayment = pv * monthlyRate / (1 - Math.pow(1 + monthlyRate, -term));
        }
        double totalPaid = monthlyPayment * term;
        double totalInterest = totalPaid - pv;

        UUID userId = securityUtils.getCurrentUserId();
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        TransactionSummaryDTO summary = transactionService.aggregates(
                userId, null, null, null,
                lastMonth.atDay(1).atStartOfDay(),
                lastMonth.plusMonths(1).atDay(1).atStartOfDay(),
                null, null, null);
        BigDecimal monthlyIncome = summary.getTotalIncome();

        Map<String, Object> out = new HashMap<>();
        out.put("principal", p);
        out.put("annualRatePct", annualPct);
        out.put("termMonths", term);
        out.put("monthlyPayment", BigDecimal.valueOf(monthlyPayment).setScale(2, java.math.RoundingMode.HALF_UP));
        out.put("totalPaid", BigDecimal.valueOf(totalPaid).setScale(2, java.math.RoundingMode.HALF_UP));
        out.put("totalInterest", BigDecimal.valueOf(totalInterest).setScale(2, java.math.RoundingMode.HALF_UP));
        out.put("interestPctOfPrincipal", BigDecimal.valueOf(totalInterest / pv * 100).setScale(2, java.math.RoundingMode.HALF_UP));
        if (monthlyIncome != null && monthlyIncome.signum() > 0) {
            out.put("monthlyIncomeReference", monthlyIncome);
            out.put("paymentPctOfMonthlyIncome",
                    BigDecimal.valueOf(monthlyPayment / monthlyIncome.doubleValue() * 100)
                            .setScale(2, java.math.RoundingMode.HALF_UP));
        }
        return out;
    }

    private int bestMonths(StrategyComparisonDTO comp) {
        int sb = comp.getSnowball() != null ? comp.getSnowball().getMonthsToFreedom() : -1;
        int av = comp.getAvalanche() != null ? comp.getAvalanche().getMonthsToFreedom() : -1;
        if (sb < 0 && av < 0) return -1;
        if (sb < 0) return av;
        if (av < 0) return sb;
        return Math.min(sb, av);
    }

    private BigDecimal bestInterest(StrategyComparisonDTO comp) {
        BigDecimal sb = comp.getSnowball() != null ? comp.getSnowball().getTotalInterest() : null;
        BigDecimal av = comp.getAvalanche() != null ? comp.getAvalanche().getTotalInterest() : null;
        if (sb == null && av == null) return BigDecimal.ZERO;
        if (sb == null) return av;
        if (av == null) return sb;
        return sb.min(av);
    }

    // ─── Wishlist

    @Tool(name = "getActiveWishlist",
          description = "Lista las metas de ahorro / wishlist activas del usuario con monto objetivo, ahorrado y % de progreso. " +
                        "Úsalo para preguntas como '¿qué metas tengo?' o '¿cuánto me falta para mi viaje?'")
    public List<Map<String, Object>> getActiveWishlist() {
        UUID userId = securityUtils.getCurrentUserId();
        return wishlistService.findAllByUser(userId, WishlistStatus.ACTIVE).stream()
                .map(this::wishAsMap)
                .toList();
    }

    

    @Tool(name = "proposeExpense",
          description = "Propone registrar un GASTO. NO lo crea — devuelve una propuesta que el usuario " +
                        "verá en pantalla con un botón para confirmar o cancelar. Usalo cuando el usuario " +
                        "diga cosas como 'apunta que gasté X', 'crea un gasto de Y', 'me cobraron Z'.")
    public String proposeExpense(
            @ToolParam(description = "Monto en COP. Solo el número, sin símbolos.") String amount,
            @ToolParam(description = "Nombre de la cuenta de la que sale la plata.") String accountName,
            @ToolParam(description = "Nombre de la categoría del gasto.") String categoryName,
            @ToolParam(required = false, description = "Descripción opcional del gasto.") String description
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", amount);
        payload.put("accountName", accountName);
        payload.put("categoryName", categoryName);
        if (description != null) payload.put("description", description);

        String summary = String.format("Crear gasto de $%s en %s (cuenta: %s)",
                amount, categoryName, accountName);
        actionCollector.add(ChatActionType.CREATE_EXPENSE, summary, payload);
        return "Propuesta de gasto registrada. El usuario verá un botón para confirmar.";
    }

    @Tool(name = "proposeIncome",
          description = "Propone registrar un INGRESO. NO lo crea — devuelve una propuesta que el usuario " +
                        "verá en pantalla con confirmación. Usalo cuando el usuario diga 'recibí X', 'ingresó Y'.")
    public String proposeIncome(
            @ToolParam(description = "Monto en COP, solo número.") String amount,
            @ToolParam(description = "Nombre de la cuenta donde entra la plata.") String accountName,
            @ToolParam(description = "Nombre de la categoría del ingreso.") String categoryName,
            @ToolParam(required = false, description = "Descripción opcional.") String description
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", amount);
        payload.put("accountName", accountName);
        payload.put("categoryName", categoryName);
        if (description != null) payload.put("description", description);

        String summary = String.format("Crear ingreso de $%s en %s (cuenta: %s)",
                amount, categoryName, accountName);
        actionCollector.add(ChatActionType.CREATE_INCOME, summary, payload);
        return "Propuesta de ingreso registrada. El usuario verá un botón para confirmar.";
    }

    @Tool(name = "proposeTransfer",
          description = "Propone una TRANSFERENCIA entre dos cuentas del usuario (incluye 'mover plata a ahorro'). " +
                        "NO la ejecuta — devuelve propuesta para confirmar. Usalo cuando el usuario diga " +
                        "'pasa X de cuenta A a B', 'mové Y a ahorro', 'guardar Z'.")
    public String proposeTransfer(
            @ToolParam(description = "Monto en COP, solo número.") String amount,
            @ToolParam(description = "Cuenta origen (de dónde sale la plata).") String fromAccountName,
            @ToolParam(description = "Cuenta destino (a dónde va la plata).") String toAccountName,
            @ToolParam(required = false, description = "Descripción opcional.") String description
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", amount);
        payload.put("fromAccountName", fromAccountName);
        payload.put("toAccountName", toAccountName);
        if (description != null) payload.put("description", description);

        String summary = String.format("Transferir $%s de %s a %s",
                amount, fromAccountName, toAccountName);
        actionCollector.add(ChatActionType.CREATE_TRANSFER, summary, payload);
        return "Propuesta de transferencia registrada. El usuario verá un botón para confirmar.";
    }

    // Documentos privados del usuario 

    @Tool(name = "listUserDocuments",
          description = "Lista los documentos (PDFs/textos) que el usuario subió a FinBot. " +
                        "Devuelve nombre y fecha de cada uno. Útil para saber qué archivos tiene disponibles.")
    public List<Map<String, Object>> listUserDocuments() {
        UUID userId = securityUtils.getCurrentUserId();
        return documentRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .filter(d -> d.getStatus().name().equals("READY"))
                .map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", d.getId());
                    m.put("name", d.getName());
                    m.put("createdAt", d.getCreatedAt());
                    m.put("chunkCount", d.getChunkCount());
                    return m;
                })
                .toList();
    }

    @Tool(name = "searchUserDocuments",
          description = "Busca contenido específico dentro de los documentos que el usuario subió. " +
                        "Hace búsqueda semántica y devuelve fragmentos relevantes con el nombre del documento. " +
                        "Úsalo cuando la pregunta es sobre información que probablemente está en un PDF subido " +
                        "(extracto bancario, contrato, factura, etc.).")
    public List<Map<String, Object>> searchUserDocuments(
            @ToolParam(description = "Lo que querés buscar en los documentos del usuario.")
            String query
    ) {
        UUID userId = securityUtils.getCurrentUserId();
        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            var filter = b.and(
                    b.eq("userId", userId.toString()),
                    b.eq("type", "document")
            ).build();

            var results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(5)
                            .filterExpression(filter)
                            .build()
            );
            if (results == null) return List.of();
            return results.stream().map(doc -> {
                Map<String, Object> m = new HashMap<>();
                m.put("documentName", doc.getMetadata().get("documentName"));
                m.put("excerpt", doc.getText());
                return m;
            }).toList();
        } catch (Exception e) {
            log.warn("Error buscando en documentos del usuario {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    // ─── Helpers 

    private Map<String, Object> accountAsMap(AccountDTO a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getName());
        m.put("bank", a.getBank());
        m.put("balance", a.getBalance());
        m.put("currency", a.getCurrency());
        m.put("isSavings", a.isSavings());
        return m;
    }

    private Map<String, Object> txAsMap(TransactionDTO t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId());
        m.put("date", t.getDate());
        m.put("amount", t.getAmount());
        m.put("type", t.getType());
        m.put("description", t.getDescription());
        m.put("category", t.getCategoryName());
        m.put("account", t.getAccountName());
        if (t.getTransferToAccountName() != null) m.put("transferTo", t.getTransferToAccountName());
        return m;
    }

    private Map<String, Object> debtAsMap(DebtDTO d) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("creditor", d.getCreditor());
        m.put("currentBalance", d.getCurrentBalance());
        m.put("annualRate", d.getAnnualRate());
        m.put("minimumPayment", d.getMinimumPayment());
        m.put("progressPercentage", d.getProgressPercentage());
        return m;
    }

    private Map<String, Object> wishAsMap(WishlistDTO w) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", w.getId());
        m.put("name", w.getName());
        m.put("targetAmount", w.getTargetAmount());
        m.put("currentAmount", w.getCurrentAmount());
        m.put("progressPercentage", w.getProgressPercentage());
        m.put("deadline", w.getDeadline());
        return m;
    }

    private Map<String, Object> planSummary(com.ExpenseTracker.app.debt.presentation.dto.PayoffPlanDTO plan) {
        Map<String, Object> m = new HashMap<>();
        m.put("strategy", plan.getStrategy());
        m.put("monthsToFreedom", plan.getMonthsToFreedom());
        m.put("totalInterest", plan.getTotalInterest());
        m.put("totalPaid", plan.getTotalPaid());
        return m;
    }

    private static <E extends Enum<E>> E parseEnum(String s, Class<E> type) {
        if (s == null || s.isBlank()) return null;
        try { return Enum.valueOf(type, s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static LocalDateTime parseDayStart(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try { return LocalDate.parse(iso).atStartOfDay(); }
        catch (Exception e) { return null; }
    }

    private static LocalDateTime parseDayEnd(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try { return LocalDate.parse(iso).plusDays(1).atStartOfDay(); }
        catch (Exception e) { return null; }
    }

    private static BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try { return new BigDecimal(s); }
        catch (NumberFormatException e) { return null; }
    }

    private static int parseInt(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) {
            try { return new BigDecimal(s.trim()).intValue(); }
            catch (Exception ignored) { return 0; }
        }
    }
}
