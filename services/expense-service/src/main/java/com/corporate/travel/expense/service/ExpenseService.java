package com.corporate.travel.expense.service;

import com.corporate.travel.expense.model.entity.Expense;
import com.corporate.travel.expense.model.entity.ExpenseItem;
import com.corporate.travel.security.SecurityContext;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for Expense operations
 */
public interface ExpenseService {
    
    /**
     * Create a new expense report
     */
    Expense createExpense(Expense expense, SecurityContext context);
    
    /**
     * Get expense by ID (with authorization check)
     */
    Expense getExpense(UUID id, SecurityContext context);
    
    /**
     * Get all expenses for the current user
     */
    List<Expense> getUserExpenses(SecurityContext context);
    
    /**
     * Update expense (only in DRAFT status)
     */
    Expense updateExpense(UUID id, Expense expenseUpdate, SecurityContext context);
    
    /**
     * Delete expense (only in DRAFT status)
     */
    void deleteExpense(UUID id, SecurityContext context);
    
    /**
     * Add an item to an expense (only in DRAFT status)
     */
    ExpenseItem addExpenseItem(UUID expenseId, ExpenseItem item, SecurityContext context);
    
    /**
     * Update an expense item (only when expense is DRAFT)
     */
    ExpenseItem updateExpenseItem(UUID expenseId, UUID itemId, ExpenseItem itemUpdate, SecurityContext context);
    
    /**
     * Delete an expense item (only when expense is DRAFT)
     */
    void deleteExpenseItem(UUID expenseId, UUID itemId, SecurityContext context);
    
    /**
     * Submit expense for approval (DRAFT → SUBMITTED)
     */
    Expense submitExpense(UUID id, SecurityContext context);
    
    /**
     * Approve expense (SUBMITTED → APPROVED)
     */
    Expense approveExpense(UUID id, String comments, SecurityContext context);
    
    /**
     * Reject expense (SUBMITTED → REJECTED)
     */
    Expense rejectExpense(UUID id, String comments, SecurityContext context);
}