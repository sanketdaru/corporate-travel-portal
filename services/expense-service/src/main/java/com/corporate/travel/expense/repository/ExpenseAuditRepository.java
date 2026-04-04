package com.corporate.travel.expense.repository;

import com.corporate.travel.expense.model.entity.ExpenseAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseAuditRepository extends JpaRepository<ExpenseAudit, UUID> {

    List<ExpenseAudit> findByExpenseIdAndTenantIdOrderByTimestampDesc(UUID expenseId, String tenantId);

    List<ExpenseAudit> findByTenantIdOrderByTimestampDesc(String tenantId);
}
