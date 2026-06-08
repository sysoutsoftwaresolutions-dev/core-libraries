package com.core.workflow.engine;

import com.core.workflow.context.StepExecutionContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates SpEL (Spring Expression Language) conditions against a workflow execution context.
 */
@Component
public class ConditionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * Evaluates a SpEL condition expression against the provided StepExecutionContext.
     *
     * @param expressionString the SpEL expression (e.g. "#context.variables['status'] == 'APPROVED'")
     * @param context          the execution context to evaluate against
     * @return true if the expression evaluates to true, false otherwise
     */
    public boolean evaluate(String expressionString, StepExecutionContext context) {
        if (expressionString == null || expressionString.strip().isEmpty()) {
            return true;
        }

        try {
            Expression expression = expressionCache.computeIfAbsent(expressionString, parser::parseExpression);
            StandardEvaluationContext evalContext = new StandardEvaluationContext();
            
            // Allow accessing the context as #context and variables as #variables or #vars
            evalContext.setVariable("context", context);
            evalContext.setVariable("variables", context.getVariables());
            evalContext.setVariable("vars", context.getVariables());

            Boolean result = expression.getValue(evalContext, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to evaluate SpEL condition: " + expressionString, e);
        }
    }
}
