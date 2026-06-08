package com.core.common.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Aspect to audit and log REST controller method invocations, measure execution
 * duration, and catch exceptions for central auditing logs before letting them
 * bubble up to the global exception handler advice.
 */
@Aspect
@Component
public class ControllerErrorHandlerAspect {

    private static final Logger log = LoggerFactory.getLogger(ControllerErrorHandlerAspect.class);

    /**
     * Pointcut that matches all methods inside classes annotated with
     * @RestController or @Controller.
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) || within(@org.springframework.stereotype.Controller *)")
    public void controllerPointcut() {}

    /**
     * Surrounds controller executions to trace arguments, time durations, and failures.
     *
     * @param joinPoint join point being executed
     * @return result of target execution
     * @throws Throwable propagated exception
     */
    @Around("controllerPointcut()")
    public Object handleControllerExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();
        
        log.info("[Controller-Audit] Entering method: {} with args: {}", methodName, Arrays.toString(args));
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            log.info("[Controller-Audit] Exiting method: {} successfully. Duration: {}ms", methodName, duration);
            return result;
        } catch (Throwable t) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[Controller-Audit] Failed method: {} in {}ms. Error: {}", 
                    methodName, duration, t.getMessage(), t);
            throw t; // Rethrow to bubble up to GlobalExceptionHandler
        }
    }
}
