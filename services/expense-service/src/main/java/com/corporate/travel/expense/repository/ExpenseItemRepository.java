package com.corporate.travel.expense.repository;

import com.corporate.travel.expense.model.entity.ExpenseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for ExpenseItem entity
 */
@Repository
public interface ExpenseItemRepository extends JpaRepository<ExpenseItem, UUID> {
    
    /**
     * Find all items for a specific expense
     */
    List<ExpenseItem> findByExpenseId(UUID expenseId);
}