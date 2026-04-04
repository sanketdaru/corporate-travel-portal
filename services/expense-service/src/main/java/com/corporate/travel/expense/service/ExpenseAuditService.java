package com.corporate.travel.expense.service;

import com.corporate.travel.expense.model.entity.ExpenseAudit;
import com.corporate.travel.security.SecurityContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ExpenseAuditService {

    void record(UUID expenseId, String action, Map<String, Object> details, SecurityContext context);

    List<ExpenseAudit> getAuditTrail(UUID expenseId, SecurityContext context);
}
