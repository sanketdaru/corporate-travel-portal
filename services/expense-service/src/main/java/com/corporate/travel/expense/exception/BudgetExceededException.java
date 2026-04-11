package com.corporate.travel.expense.exception;

import java.math.BigDecimal;

/**
 * Thrown when an expense report's total amount exceeds the pre-approved
 * budget of its linked travel authorization.
 */
public class BudgetExceededException extends RuntimeException {

    private final BigDecimal budget;
    private final BigDecimal total;
    private final String currency;

    public BudgetExceededException(BigDecimal budget, BigDecimal total, String currency) {
        super(String.format(
            "Expense total %s %.2f exceeds approved travel budget of %s %.2f (overage: %.2f)",
            currency, total, currency, budget, total.subtract(budget)
        ));
        this.budget   = budget;
        this.total    = total;
        this.currency = currency;
    }

    public BigDecimal getBudget()   { return budget; }
    public BigDecimal getTotal()    { return total; }
    public String     getCurrency() { return currency; }
    public BigDecimal getOverage()  { return total.subtract(budget); }
}
