package com.corporate.travel.expense.repository;

import com.corporate.travel.expense.model.entity.Expense;
import com.corporate.travel.models.ExpenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Expense entity
 */
@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    
    /**
     * Find expense by ID and tenant (for multi-tenant isolation)
     */
    Optional<Expense> findByIdAndTenantId(UUID id, String tenantId);
    
    /**
     * Find all expenses for a user in a tenant
     */
    List<Expense> findByTenantIdAndUserId(String tenantId, String userId);
    
    /**
     * Find all expenses in a tenant
     */
    List<Expense> findByTenantId(String tenantId);
    
    /**
     * Find expenses by status in a tenant
     */
    List<Expense> findByTenantIdAndStatus(String tenantId, ExpenseStatus status);
}
