package com.ExpenseTracker.app.debt.service;

import com.ExpenseTracker.app.debt.mapper.DebtMapper;
import com.ExpenseTracker.app.debt.persistence.entity.DebtEntity;
import com.ExpenseTracker.app.debt.persistence.repository.DebtEntityRepository;
import com.ExpenseTracker.app.debt.presentation.dto.*;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.util.enums.DebtStatus;
import com.ExpenseTracker.util.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class DebtServiceImpl implements IDebtService {

    private static final int MAX_MONTHS = 600; // 50 años: tope para evitar bucles cuando min < intereses
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private final DebtEntityRepository debtRepository;
    private final UserEntityRepository userRepository;
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
