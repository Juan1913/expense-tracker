package com.ExpenseTracker.util.aop;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class PerformanceAspect {

    private final MeterRegistry meterRegistry;

    @Around("execution(* com.ExpenseTracker.app.*.service.*ServiceImpl.*(..))")
    public Object measureAndRecord(ProceedingJoinPoint jp) throws Throwable {
        String className = jp.getTarget().getClass().getSimpleName();
        String methodName = jp.getSignature().getName();
        Timer.Sample sample = Timer.start(meterRegistry);
        long start = System.currentTimeMillis();
        try {
            Object result = jp.proceed();
            log.debug("[PERF] {}.{}() → {}ms", className, methodName, System.currentTimeMillis() - start);
            return result;
        } finally {
            sample.stop(meterRegistry.timer("service.duration",
                    "class", className, "method", methodName));
        }
    }
}
