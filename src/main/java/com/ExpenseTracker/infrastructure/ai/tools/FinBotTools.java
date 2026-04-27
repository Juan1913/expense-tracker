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
          description = "Patrimonio total: suma de saldos en cuentas operativas vs ahorro. " +
                        "Úsalo para responder '¿cuánto tengo en total?' o '¿cuánto tengo ahorrado?'")
    public Map<String, Object> getNetWorthSummary() {
        UUID userId = securityUtils.getCurrentUserId();
        var accounts = accountService.findAllByUser(userId);
        BigDecimal savings = BigDecimal.ZERO;
        BigDecimal operational = BigDecimal.ZERO;
        for (AccountDTO a : accounts) {
            BigDecimal bal = a.getBalance() == null ? BigDecimal.ZERO : a.getBalance();
            if (a.isSavings()) savings = savings.add(bal);
            else operational = operational.add(bal);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("totalNetWorth", operational.add(savings));
        out.put("operationalBalance", operational);
        out.put("savingsBalance", savings);
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
}
