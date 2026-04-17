package com.ExpenseTracker.util.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    private static final String SERVICE_POINTCUT =
            "execution(* com.ExpenseTracker.app.*.service.*ServiceImpl.*(..))";

    @Before(SERVICE_POINTCUT)
    public void logBefore(JoinPoint jp) {
        log.debug("[{}] → {}({})",
                jp.getTarget().getClass().getSimpleName(),
                jp.getSignature().getName(),
                Arrays.toString(jp.getArgs()));
    }

    @AfterReturning(pointcut = SERVICE_POINTCUT, returning = "result")
    public void logAfterReturning(JoinPoint jp, Object result) {
        log.debug("[{}] ← {}() OK",
                jp.getTarget().getClass().getSimpleName(),
                jp.getSignature().getName());
    }

    @AfterThrowing(pointcut = SERVICE_POINTCUT, throwing = "ex")
    public void logException(JoinPoint jp, Exception ex) {
        log.error("[{}] ✗ {}() → {}: {}",
                jp.getTarget().getClass().getSimpleName(),
                jp.getSignature().getName(),
                ex.getClass().getSimpleName(),
                ex.getMessage());
    }
}
