package com.corporate.travel.expense.service.impl;

import com.corporate.travel.expense.model.entity.ExpenseAudit;
import com.corporate.travel.expense.repository.ExpenseAuditRepository;
import com.corporate.travel.expense.service.ExpenseAuditService;
import com.corporate.travel.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseAuditServiceImpl implements ExpenseAuditService {

    private final ExpenseAuditRepository auditRepository;

    /**
     * Records an expense audit event within the caller's transaction.
     * Must share the same transaction as the expense write so the FK
     * expense_audit.expense_id → expenses.id is satisfied before commit.
     */
    @Override
    @Transactional
    public void record(UUID expenseId, String action, Map<String, Object> details, SecurityContext context) {
        String actorId   = context.getActorId() != null ? context.getActorId() : context.getUserId();
        String subjectId = context.getSubjectId() != null ? context.getSubjectId() : context.getUserId();

        UUID delegationId = null;
        if (context.getDelegationId() != null) {
            try { delegationId = UUID.fromString(context.getDelegationId()); } catch (IllegalArgumentException ignored) {}
        }

        UUID consentId = null;
        if (context.getConsentId() != null) {
            try { consentId = UUID.fromString(context.getConsentId()); } catch (IllegalArgumentException ignored) {}
        }

        ExpenseAudit audit = ExpenseAudit.builder()
                .expenseId(expenseId)
                .actorId(actorId)
                .subjectId(subjectId)
                .action(action)
                .details(details)
                .tenantId(context.getTenantId())
                .delegationId(delegationId)
                .consentId(consentId)
                .build();

        auditRepository.save(audit);
        log.debug("Audit recorded: expense={} action={} actor={} subject={}", expenseId, action, actorId, subjectId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseAudit> getAuditTrail(UUID expenseId, SecurityContext context) {
        return auditRepository.findByExpenseIdAndTenantIdOrderByTimestampDesc(expenseId, context.getTenantId());
    }
}
