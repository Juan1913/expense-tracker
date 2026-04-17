package com.ExpenseTracker.infrastructure.ai.rag;

import com.ExpenseTracker.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class FinancialIndexingAspect {

    private final FinancialIndexingService indexingService;
    private final SecurityUtils securityUtils;

    @AfterReturning(
            pointcut = "execution(* com.ExpenseTracker.app.transaction.service.*ServiceImpl.create*(..)) || " +
                       "execution(* com.ExpenseTracker.app.transaction.service.*ServiceImpl.update*(..)) || " +
                       "execution(* com.ExpenseTracker.app.transaction.service.*ServiceImpl.delete*(..)) || " +
                       "execution(* com.ExpenseTracker.app.budget.service.*ServiceImpl.create*(..)) || " +
                       "execution(* com.ExpenseTracker.app.budget.service.*ServiceImpl.update*(..)) || " +
                       "execution(* com.ExpenseTracker.app.budget.service.*ServiceImpl.delete*(..)) || " +
                       "execution(* com.ExpenseTracker.app.wishlist.service.*ServiceImpl.create*(..)) || " +
                       "execution(* com.ExpenseTracker.app.wishlist.service.*ServiceImpl.update*(..)) || " +
                       "execution(* com.ExpenseTracker.app.wishlist.service.*ServiceImpl.delete*(..)) || " +
                       "execution(* com.ExpenseTracker.app.account.service.*ServiceImpl.create*(..)) || " +
                       "execution(* com.ExpenseTracker.app.account.service.*ServiceImpl.update*(..)) || " +
                       "execution(* com.ExpenseTracker.app.account.service.*ServiceImpl.delete*(..))"
    )
    public void triggerReindex() {
        try {
            indexingService.reindexUser(securityUtils.getCurrentUserId());
        } catch (Exception e) {
            log.warn("No se pudo disparar re-indexación automática: {}", e.getMessage());
        }
    }
}
