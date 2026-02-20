package com.corporate.travel.expense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Expense Service Application
 * 
 * Manages expense reports and expense items with approval workflow.
 * Supports multi-tenant isolation and delegated identity patterns.
 */
@SpringBootApplication(scanBasePackages = {
    "com.corporate.travel.expense",
    "com.corporate.travel.security"
})
public class ExpenseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseServiceApplication.class, args);
    }

}