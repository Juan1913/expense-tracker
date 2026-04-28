package com.ExpenseTracker.app.debt.service;

import com.ExpenseTracker.app.account.persistence.entity.AccountEntity;
import com.ExpenseTracker.app.account.persistence.repository.AccountEntityRepository;
import com.ExpenseTracker.app.category.persistence.entity.CategoryEntity;
import com.ExpenseTracker.app.category.persistence.repository.CategoryEntityRepository;
import com.ExpenseTracker.app.debt.mapper.DebtMapper;
import com.ExpenseTracker.app.debt.persistence.entity.DebtEntity;
import com.ExpenseTracker.app.debt.persistence.entity.DebtPaymentEntity;
import com.ExpenseTracker.app.debt.persistence.repository.DebtEntityRepository;
import com.ExpenseTracker.app.debt.persistence.repository.DebtPaymentRepository;
import com.ExpenseTracker.app.debt.presentation.dto.*;
import com.ExpenseTracker.app.transaction.presentation.dto.CreateTransactionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionDTO;
import com.ExpenseTracker.app.transaction.service.ITransactionService;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.util.enums.DebtStatus;
import com.ExpenseTracker.util.enums.TransactionType;
import com.ExpenseTracker.util.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class DebtServiceImpl implements IDebtService {

    private static final int MAX_MONTHS = 600;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private final DebtEntityRepository debtRepository;
    private final DebtPaymentRepository debtPaymentRepository;
    private final UserEntityRepository userRepository;
    private final AccountEntityRepository accountRepository;
    private final CategoryEntityRepository categoryRepository;
    private final ITransactionService transactionService;
    private final DebtMapper debtMapper;

    // ─── CRUD ────────────────────────────────────────────────────────────────

    @Override
    public DebtDTO create(CreateDebtDTO dto, UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado con id: " + userId));

        DebtEntity entity = debtMapper.toEntity(dto);
        entity.setUser(user);
        if (entity.getCurrentBalance() == null) {
            entity.setCurrentBalance(entity.getPrincipal());
        }
        return debtMapper.toDTO(debtRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DebtDTO> findAllByUser(UUID userId, DebtStatus status) {
        if (status != null) {
            return debtRepository.findByUser_IdAndStatusOrderByCreatedAtDesc(userId, status)
                    .stream().map(debtMapper::toDTO).toList();
        }
        return debtRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream().map(debtMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DebtDTO findById(UUID id, UUID userId) {
        return debtRepository.findByIdAndUser_Id(id, userId)
                .map(debtMapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Deuda no encontrada con id: " + id));
    }

    @Override
    public DebtDTO update(UUID id, UpdateDebtDTO dto, UUID userId) {
        DebtEntity entity = debtRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Deuda no encontrada con id: " + id));
        debtMapper.updateEntityFromDTO(dto, entity);
        // Auto-marcar PAID_OFF si el saldo llegó a 0
        if (entity.getCurrentBalance() != null
                && entity.getCurrentBalance().compareTo(BigDecimal.ZERO) <= 0
                && entity.getStatus() != DebtStatus.PAID_OFF) {
            entity.setStatus(DebtStatus.PAID_OFF);
        }
        return debtMapper.toDTO(debtRepository.save(entity));
    }

    @Override
    public void delete(UUID id, UUID userId) {
        DebtEntity entity = debtRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Deuda no encontrada con id: " + id));
        debtRepository.delete(entity);
    }

    // ─── Comparación de estrategias ──────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public StrategyComparisonDTO compareStrategies(UUID userId, BigDecimal extraBudget) {
        List<DebtEntity> debts = debtRepository
                .findByUser_IdAndStatusOrderByCreatedAtDesc(userId, DebtStatus.ACTIVE)
                .stream()
                .filter(d -> d.getCurrentBalance() != null && d.getCurrentBalance().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        BigDecimal totalMinimum = debts.stream()
                .map(DebtEntity::getMinimumPayment)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal extra = extraBudget == null ? BigDecimal.ZERO : extraBudget.max(BigDecimal.ZERO);
        BigDecimal monthlyBudget = totalMinimum.add(extra);

        PayoffPlanDTO minPlan      = simulate(debts, BigDecimal.ZERO, Strategy.MINIMUM);
        PayoffPlanDTO snowballPlan = simulate(debts, extra, Strategy.SNOWBALL);
        PayoffPlanDTO avalanchePlan= simulate(debts, extra, Strategy.AVALANCHE);

        // Recomendar la de menor interés total (empate → menor meses)
        PayoffPlanDTO best = snowballPlan;
        if (avalanchePlan.getTotalInterest().compareTo(best.getTotalInterest()) < 0) best = avalanchePlan;
        else if (avalanchePlan.getTotalInterest().compareTo(best.getTotalInterest()) == 0
                && avalanchePlan.getMonthsToFreedom() < best.getMonthsToFreedom()) best = avalanchePlan;

        BigDecimal interestSaved = minPlan.getTotalInterest().subtract(best.getTotalInterest());
        int monthsSaved = minPlan.getMonthsToFreedom() - best.getMonthsToFreedom();

        return StrategyComparisonDTO.builder()
                .monthlyBudget(monthlyBudget)
                .totalMinimum(totalMinimum)
                .extraBudget(extra)
                .minimumOnly(minPlan)
                .snowball(snowballPlan)
                .avalanche(avalanchePlan)
                .recommended(best.getStrategy())
                .interestSavedVsMinimum(interestSaved.max(BigDecimal.ZERO))
                .monthsSavedVsMinimum(Math.max(0, monthsSaved))
                .build();
    }

    // ─── Núcleo numérico ─────────────────────────────────────────────────────

    private enum Strategy { MINIMUM, SNOWBALL, AVALANCHE }

    /**
     * Simulación mes a mes. Cada deuda mantiene su saldo restante; cada mes:
     *   1. Acumula interés:  balance ← balance · (1 + r_mensual)
     *   2. Aplica el pago mínimo de cada deuda (capado al saldo).
     *   3. Aplica el "extra" sobre la deuda prioritaria según estrategia
     *      (saldo más bajo en snowball, mayor tasa en avalanche). El extra
     *      INCLUYE los mínimos liberados por deudas ya saldadas (efecto bola
     *      de nieve), porque el presupuesto mensual total es constante.
     *
     * Tasa mensual desde anual efectiva:  r_m = (1 + r_a)^(1/12) − 1
     */
    private PayoffPlanDTO simulate(List<DebtEntity> debts, BigDecimal extraBudget, Strategy strat) {
        // Snapshots locales para no mutar los entities
        List<SimDebt> sim = new ArrayList<>(debts.size());
        for (DebtEntity d : debts) {
            sim.add(new SimDebt(
                    d.getId(),
                    d.getName(),
                    d.getCurrentBalance(),
                    monthlyRate(d.getAnnualRate()),
                    nullSafe(d.getMinimumPayment())
            ));
        }

        BigDecimal totalMinOriginal = sim.stream()
                .map(s -> s.minimumPayment).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Para snowball/avalanche, monthlyBudget incluye los mínimos + el extra.
        // Para MINIMUM, el extra es 0 → solo paga mínimos.
        BigDecimal monthlyBudget = totalMinOriginal.add(nullSafe(extraBudget));

        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        List<PayoffPlanDTO.MonthlyBalancePoint> trajectory = new ArrayList<>();
        List<PayoffPlanDTO.DebtPayoffOrder> order = new ArrayList<>();

        // Punto t=0
        trajectory.add(PayoffPlanDTO.MonthlyBalancePoint.builder()
                .month(0)
                .balance(totalBalance(sim))
                .interestThisMonth(BigDecimal.ZERO)
                .build());

        int month = 0;
        while (anyActive(sim) && month < MAX_MONTHS) {
            month++;

            // 1) Acumular intereses
            BigDecimal interestThisMonth = BigDecimal.ZERO;
            for (SimDebt d : sim) {
                if (d.balance.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal interest = d.balance.multiply(d.monthlyRate);
                d.interestPaid = d.interestPaid.add(interest);
                d.balance = d.balance.add(interest);
                interestThisMonth = interestThisMonth.add(interest);
            }
            totalInterest = totalInterest.add(interestThisMonth);

            // 2) Disponible para pagar este mes
            BigDecimal available = strat == Strategy.MINIMUM ? totalMinOriginal : monthlyBudget;

            // 2a) Pago mínimo en cada deuda (capado al saldo)
            for (SimDebt d : sim) {
                if (d.balance.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal pay = d.minimumPayment.min(d.balance);
                pay = pay.min(available);
                d.balance = d.balance.subtract(pay).max(BigDecimal.ZERO);
                available = available.subtract(pay);
                totalPaid = totalPaid.add(pay);
            }

            // 2b) Aplicar excedente sobre la deuda prioritaria (snowball/avalanche)
            if (strat != Strategy.MINIMUM && available.compareTo(BigDecimal.ZERO) > 0) {
                while (available.compareTo(new BigDecimal("0.01")) > 0) {
                    SimDebt target = pickPriority(sim, strat);
                    if (target == null) break;
                    BigDecimal pay = available.min(target.balance);
                    target.balance = target.balance.subtract(pay).max(BigDecimal.ZERO);
                    available = available.subtract(pay);
                    totalPaid = totalPaid.add(pay);
                    if (target.balance.compareTo(BigDecimal.ZERO) > 0) break;
                }
            }

            // 3) Registrar deudas que cayeron a 0 este mes
            for (SimDebt d : sim) {
                if (!d.paidOff && d.balance.compareTo(BigDecimal.ZERO) <= 0) {
                    d.paidOff = true;
                    order.add(PayoffPlanDTO.DebtPayoffOrder.builder()
                            .debtId(d.id).name(d.name)
                            .payoffMonth(month)
                            .interestPaid(d.interestPaid.setScale(2, RM))
                            .build());
                }
            }

            // Trayectoria
            trajectory.add(PayoffPlanDTO.MonthlyBalancePoint.builder()
                    .month(month)
                    .balance(totalBalance(sim))
                    .interestThisMonth(interestThisMonth.setScale(2, RM))
                    .build());
        }

        // Si quedó deuda al cap, agregarla al "order" como no liquidada
        for (SimDebt d : sim) {
            if (!d.paidOff) {
                order.add(PayoffPlanDTO.DebtPayoffOrder.builder()
                        .debtId(d.id).name(d.name)
                        .payoffMonth(-1) // bandera: no se liquida en el horizonte
                        .interestPaid(d.interestPaid.setScale(2, RM))
                        .build());
            }
        }

        return PayoffPlanDTO.builder()
                .strategy(strat == Strategy.MINIMUM ? "MINIMUM_ONLY"
                        : strat == Strategy.SNOWBALL ? "SNOWBALL" : "AVALANCHE")
                .monthlyTotal((strat == Strategy.MINIMUM ? totalMinOriginal : monthlyBudget).setScale(2, RM))
                .monthsToFreedom(month)
                .totalPaid(totalPaid.setScale(2, RM))
                .totalInterest(totalInterest.setScale(2, RM))
                .order(order)
                .trajectory(trajectory)
                .build();
    }

    private static SimDebt pickPriority(List<SimDebt> sim, Strategy strat) {
        SimDebt best = null;
        for (SimDebt d : sim) {
            if (d.balance.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (best == null) { best = d; continue; }
            if (strat == Strategy.SNOWBALL) {
                if (d.balance.compareTo(best.balance) < 0) best = d;
            } else if (strat == Strategy.AVALANCHE) {
                if (d.monthlyRate.compareTo(best.monthlyRate) > 0) best = d;
            }
        }
        return best;
    }

    private static boolean anyActive(List<SimDebt> sim) {
        for (SimDebt d : sim) if (d.balance.compareTo(BigDecimal.ZERO) > 0) return true;
        return false;
    }

    private static BigDecimal totalBalance(List<SimDebt> sim) {
        BigDecimal s = BigDecimal.ZERO;
        for (SimDebt d : sim) s = s.add(d.balance);
        return s.setScale(2, RM);
    }

    private static BigDecimal monthlyRate(BigDecimal annual) {
        if (annual == null || annual.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        double r = Math.pow(1.0 + annual.doubleValue(), 1.0 / 12.0) - 1.0;
        return BigDecimal.valueOf(r).setScale(10, RM);
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Snapshot mutable de una deuda, sólo para la simulación. */
    @Override
    public DebtPaymentDTO recordPayment(UUID debtId, CreateDebtPaymentDTO dto, UUID userId) {
        DebtEntity debt = debtRepository.findByIdAndUser_Id(debtId, userId)
                .orElseThrow(() -> new NotFoundException("Deuda no encontrada"));
        if (debt.getStatus() == DebtStatus.PAID_OFF) {
            throw new IllegalArgumentException("Esta deuda ya está saldada");
        }
        AccountEntity account = accountRepository.findByIdAndUser_Id(dto.getAccountId(), userId)
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada"));

        BigDecimal payment = dto.getAmount();
        if (payment == null || payment.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser positivo");
        }

        LocalDate paymentDate = dto.getPaymentDate() != null ? dto.getPaymentDate() : LocalDate.now();
        BigDecimal balance = debt.getCurrentBalance() == null ? BigDecimal.ZERO : debt.getCurrentBalance();

        LocalDate accrualFrom = debtPaymentRepository.findByDebt_IdOrderByPaymentDateDesc(debtId)
                .stream().findFirst()
                .map(DebtPaymentEntity::getPaymentDate)
                .orElseGet(() -> debt.getStartDate() != null
                        ? debt.getStartDate()
                        : debt.getCreatedAt().toLocalDate());

        long daysSince = Math.max(0, ChronoUnit.DAYS.between(accrualFrom, paymentDate));
        BigDecimal monthlyRate = monthlyRate(debt.getAnnualRate());
        BigDecimal interestAccrued = balance
                .multiply(monthlyRate)
                .multiply(BigDecimal.valueOf(daysSince))
                .divide(BigDecimal.valueOf(30), 2, RM);

        BigDecimal interestPart = payment.min(interestAccrued).max(BigDecimal.ZERO);
        BigDecimal capitalPart = payment.subtract(interestPart);
        BigDecimal newBalance = balance.add(interestAccrued).subtract(payment).max(BigDecimal.ZERO);

        debt.setCurrentBalance(newBalance);
        if (newBalance.signum() == 0) {
            debt.setStatus(DebtStatus.PAID_OFF);
        }
        debtRepository.save(debt);

        UUID transactionId = null;
        try {
            CategoryEntity category = ensureDebtPaymentCategory(debt.getUser(), userId);
            CreateTransactionDTO txDto = CreateTransactionDTO.builder()
                    .amount(payment)
                    .date(paymentDate.atStartOfDay())
                    .description("Pago: " + debt.getName())
                    .type(TransactionType.EXPENSE)
                    .accountId(account.getId())
                    .categoryId(category.getId())
                    .build();
            TransactionDTO tx = transactionService.create(txDto, userId);
            transactionId = tx.getId();
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo registrar el pago: " + e.getMessage());
        }

        DebtPaymentEntity record = DebtPaymentEntity.builder()
                .debt(debt)
                .user(debt.getUser())
                .amountTotal(payment)
                .amountInterest(interestPart)
                .amountCapital(capitalPart)
                .balanceAfter(newBalance)
                .paymentDate(paymentDate)
                .account(account)
                .transactionId(transactionId)
                .build();
        DebtPaymentEntity saved = debtPaymentRepository.save(record);
        return toPaymentDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DebtPaymentDTO> listPayments(UUID debtId, UUID userId) {
        debtRepository.findByIdAndUser_Id(debtId, userId)
                .orElseThrow(() -> new NotFoundException("Deuda no encontrada"));
        return debtPaymentRepository.findByDebt_IdOrderByPaymentDateDesc(debtId).stream()
                .map(this::toPaymentDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DebtSummaryDTO summary(UUID debtId, UUID userId) {
        DebtEntity debt = debtRepository.findByIdAndUser_Id(debtId, userId)
                .orElseThrow(() -> new NotFoundException("Deuda no encontrada"));

        BigDecimal capital = debtPaymentRepository.sumCapitalByDebt(debtId);
        BigDecimal interest = debtPaymentRepository.sumInterestByDebt(debtId);
        int count = debtPaymentRepository.findByDebt_IdOrderByPaymentDateDesc(debtId).size();

        BigDecimal monthlyRate = monthlyRate(debt.getAnnualRate());
        BigDecimal balance = debt.getCurrentBalance() == null ? BigDecimal.ZERO : debt.getCurrentBalance();
        BigDecimal nextMonthInterest = balance.multiply(monthlyRate).setScale(2, RM);

        BigDecimal pct = BigDecimal.ZERO;
        if (debt.getPrincipal() != null && debt.getPrincipal().signum() > 0) {
            pct = capital
                    .multiply(BigDecimal.valueOf(100))
                    .divide(debt.getPrincipal(), 2, RM);
        }

        String[] q = qualityFor(debt.getAnnualRate());

        return DebtSummaryDTO.builder()
                .debtId(debtId)
                .totalCapitalPaid(capital)
                .totalInterestPaid(interest)
                .currentBalance(balance)
                .nextMonthInterestEstimate(nextMonthInterest)
                .capitalProgressPercentage(pct)
                .paymentsCount(count)
                .qualityBadge(q[0])
                .qualityHint(q[1])
                .build();
    }

    public static String[] qualityFor(BigDecimal annualRate) {
        if (annualRate == null) return new String[]{"MEDIUM", "Sin tasa registrada — no se puede clasificar."};
        double rate = annualRate.doubleValue();
        if (rate < 0.15) return new String[]{"GOOD", "Tasa baja, deuda razonable. Útil si financia algo que gana valor."};
        if (rate < 0.25) return new String[]{"MEDIUM", "Tasa media. Pagala antes de tomar más deuda nueva."};
        return new String[]{"BAD", "Tasa alta — deuda cara. Priorizá liquidarla cuanto antes."};
    }

    private CategoryEntity ensureDebtPaymentCategory(UserEntity user, UUID userId) {
        return categoryRepository.findByUser_IdAndType(userId, "EXPENSE").stream()
                .filter(c -> "Pago de deudas".equalsIgnoreCase(c.getName()))
                .findFirst()
                .orElseGet(() -> {
                    CategoryEntity cat = CategoryEntity.builder()
                            .name("Pago de deudas")
                            .type("EXPENSE")
                            .user(user)
                            .build();
                    return categoryRepository.save(cat);
                });
    }

    private DebtPaymentDTO toPaymentDTO(DebtPaymentEntity p) {
        return DebtPaymentDTO.builder()
                .id(p.getId())
                .debtId(p.getDebt() != null ? p.getDebt().getId() : null)
                .amountTotal(p.getAmountTotal())
                .amountInterest(p.getAmountInterest())
                .amountCapital(p.getAmountCapital())
                .balanceAfter(p.getBalanceAfter())
                .paymentDate(p.getPaymentDate())
                .accountId(p.getAccount() != null ? p.getAccount().getId() : null)
                .accountName(p.getAccount() != null ? p.getAccount().getName() : null)
                .transactionId(p.getTransactionId())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private static final class SimDebt {
        final UUID id;
        final String name;
        BigDecimal balance;
        final BigDecimal monthlyRate;
        final BigDecimal minimumPayment;
        BigDecimal interestPaid = BigDecimal.ZERO;
        boolean paidOff = false;

        SimDebt(UUID id, String name, BigDecimal balance, BigDecimal monthlyRate, BigDecimal minimumPayment) {
            this.id = id; this.name = name;
            this.balance = balance == null ? BigDecimal.ZERO : balance;
            this.monthlyRate = monthlyRate;
            this.minimumPayment = minimumPayment == null ? BigDecimal.ZERO : minimumPayment;
        }
    }
}
