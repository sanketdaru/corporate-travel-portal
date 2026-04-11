package com.corporate.travel.expense.service.impl;

import com.corporate.travel.expense.client.TravelServiceClient;
import com.corporate.travel.expense.exception.BudgetExceededException;
import com.corporate.travel.expense.exception.ExpenseItemNotFoundException;
import com.corporate.travel.expense.exception.ExpenseNotFoundException;
import com.corporate.travel.expense.exception.InvalidExpenseStatusException;
import com.corporate.travel.expense.model.entity.Expense;
import com.corporate.travel.expense.model.entity.ExpenseItem;
import com.corporate.travel.expense.repository.ExpenseItemRepository;
import com.corporate.travel.expense.repository.ExpenseRepository;
import com.corporate.travel.expense.service.ExpenseAuditService;
import com.corporate.travel.expense.service.ExpenseService;
import com.corporate.travel.models.ExpenseStatus;
import com.corporate.travel.security.OpaClient;
import com.corporate.travel.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of ExpenseService with OPA authorization
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ExpenseServiceImpl implements ExpenseService {
    
    private final ExpenseRepository expenseRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final OpaClient opaClient;
    private final ExpenseAuditService auditService;
    private final TravelServiceClient travelServiceClient;
    
    @Override
    public Expense createExpense(Expense expense, SecurityContext context) {
        log.info("Creating expense for user: {}, tenant: {}", context.getUserId(), context.getTenantId());
        
        expense.setTenantId(context.getTenantId());
        String ownerId = context.getSubjectId() != null ? context.getSubjectId() : context.getUserId();
        expense.setUserId(ownerId);
        expense.setCreatedBy(context.getUserId());
        expense.setUpdatedBy(context.getUserId());
        
        if (expense.getStatus() == null) {
            expense.setStatus(ExpenseStatus.DRAFT);
        }
        
        Map<String, Object> resource = Map.of("type", "expense", "tenant_id", expense.getTenantId(), "user_id", expense.getUserId());
        
        if (!opaClient.authorize(context, "create_expense", resource)) {
            throw new AccessDeniedException("Not authorized to create expenses");
        }
        
        Expense saved = expenseRepository.save(expense);

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("status", saved.getStatus().toString());
        auditDetails.put("title", saved.getTitle());
        auditService.record(saved.getId(), "CREATE", auditDetails, context);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Expense getExpense(UUID id, SecurityContext context) {
        Expense expense = expenseRepository.findByIdAndTenantId(id, context.getTenantId())
            .orElseThrow(() -> new ExpenseNotFoundException(id));
        
        Map<String, Object> resource = Map.of("type", "expense", "id", expense.getId().toString(), 
            "tenant_id", expense.getTenantId(), "user_id", expense.getUserId(), "status", expense.getStatus().toString());
        
        if (!opaClient.authorize(context, "view_expense", resource)) {
            throw new AccessDeniedException("Not authorized to view this expense");
        }
        
        return expense;
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Expense> getUserExpenses(SecurityContext context) {
        String targetUserId = context.getSubjectId() != null ? context.getSubjectId() : context.getUserId();
        
        Map<String, Object> resource = Map.of("type", "expense", "tenant_id", context.getTenantId(), "user_id", targetUserId);
        
        if (!opaClient.authorize(context, "view_expense", resource)) {
            throw new AccessDeniedException("Not authorized to list expenses");
        }
        
        return expenseRepository.findByTenantIdAndUserId(context.getTenantId(), targetUserId);
    }
    
    @Override
    public Expense updateExpense(UUID id, Expense expenseUpdate, SecurityContext context) {
        Expense expense = expenseRepository.findByIdAndTenantId(id, context.getTenantId())
            .orElseThrow(() -> new ExpenseNotFoundException(id));
        
        if (expense.getStatus() != ExpenseStatus.DRAFT) {
            throw new InvalidExpenseStatusException("Can only update expenses in DRAFT status");
        }
        
        Map<String, Object> resource = Map.of("type", "expense", "id", expense.getId().toString(),
            "tenant_id", expense.getTenantId(), "user_id", expense.getUserId(), "status", expense.getStatus().toString());
        
        if (!opaClient.authorize(context, "update_expense", resource)) {
            throw new AccessDeniedException("Not authorized to update this expense");
        }
        
        if (expenseUpdate.getTitle() != null) expense.setTitle(expenseUpdate.getTitle());
        if (expenseUpdate.getDescription() != null) expense.setDescription(expenseUpdate.getDescription());
        if (expenseUpdate.getCurrency() != null) expense.setCurrency(expenseUpdate.getCurrency());
        if (expenseUpdate.getBookingId() != null) expense.setBookingId(expenseUpdate.getBookingId());
        expense.setUpdatedBy(context.getUserId());

        Expense updated = expenseRepository.save(expense);
        auditService.record(updated.getId(), "UPDATE", Map.of("title", updated.getTitle()), context);
        return updated;
    }

    @Override
    public void deleteExpense(UUID id, SecurityContext context) {
        Expense expense = expenseRepository.findByIdAndTenantId(id, context.getTenantId())
            .orElseThrow(() -> new ExpenseNotFoundException(id));
        
        if (expense.getStatus() != ExpenseStatus.DRAFT) {
            throw new InvalidExpenseStatusException("Can only delete expenses in DRAFT status");
        }
        
        Map<String, Object> resource = Map.of("type", "expense", "id", expense.getId().toString(),
            "tenant_id", expense.getTenantId(), "user_id", expense.getUserId(), "status", expense.getStatus().toString());
        
        if (!opaClient.authorize(context, "delete_expense", resource)) {
            throw new AccessDeniedException("Not authorized to delete this expense");
        }
        
        expenseRepository.delete(expense);
        auditService.record(id, "DELETE", Map.of("status", expense.getStatus().toString()), context);
    }
    
    @Override
    public ExpenseItem addExpenseItem(UUID expenseId, ExpenseItem item, SecurityContext context) {
        Expense expense = expenseRepository.findByIdAndTenantId(expenseId, context.getTenantId())
            .orElseThrow(() -> new ExpenseNotFoundException(expenseId));
        
        if (expense.getStatus() != ExpenseStatus.DRAFT) {
            throw new InvalidExpenseStatusException("Can only add items to expenses in DRAFT status");
        }
        
        Map<String, Object> resource = Map.of("type", "expense", "id", expense.getId().toString(),
            "tenant_id", expense.getTenantId(), "user_id", expense.getUserId(), "status", expense.getStatus().toString());
        
        if (!opaClient.authorize(context, "update_expense", resource)) {
            throw new AccessDeniedException("Not authorized to add items to this expense");
        }
        
        expense.addItem(item);
        expenseRepository.save(expense);
        
        return item;
    }
    
    @Override
    public ExpenseItem updateExpenseItem(UUID expenseId, UUID itemId, ExpenseItem itemUpdate, SecurityContext context) {
        Expense expense = expenseRepository.findByIdAndTenantId(expenseId, context.getTenantId())
            .orElseThrow(() -> new ExpenseNotFoundException(expenseId));
        
        if (expense.getStatus() != ExpenseStatus.DRAFT) {
            throw new InvalidExpenseStatusException("Can only update items in expenses with DRAFT status");
        }
        
        Map<String, Object> resource = Map.of("type", "expense", "id", expense.getId().toString(),
            "tenant_id", expense.getTenantId(), "user_id", expense.getUserId(), "status", expense.getStatus().toString());
        
        if (!opaClient.authorize(context, "update_expense", resource)) {
            throw new AccessDeniedException("Not authorized to update items in this expense");
        }
        
        ExpenseItem item = expense.getItems().stream()
            .filter(i -> i.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() -> new ExpenseItemNotFoundException(itemId));
        
        if (itemUpdate.getDate() != null) item.setDate(itemUpdate.getDate());
        if (itemUpdate.getCategory() != null) item.setCategory(itemUpdate.getCategory());
        if (itemUpdate.getDescription() != null) item.setDescription(itemUpdate.getDescription());
        if (itemUpdate.getAmount() != null) item.setAmount(itemUpdate.getAmount());
        if (itemUpdate.getCurrency() != null) item.setCurrency(itemUpdate.getCurrency());
        if (itemUpdate.getReceiptUrl() != null) item.setReceiptUrl(itemUpdate.getReceiptUrl());
        
        expense.recalculateTotal();
        expenseRepository.save(expense);
        
        return item;
    }
    
    @Override
    public void deleteExpenseItem(UUID expenseId, UUID itemId, SecurityContext context) {
        Expense expense = expenseRepository.findByIdAndTenantId(expenseId, context.getTenantId())
            .orElseThrow(() -> new ExpenseNotFoundException(expenseId));
        
        if (expense.getStatus() != ExpenseStatus.DRAFT) {
            throw new InvalidExpenseStatusException("Can only delete items from expenses with DRAFT status");
        }
        
        Map<String, Object> resource = Map.of("type", "expense", "id", expense.getId().toString(),
            "tenant_id", expense.getTenantId(), "user_id", expense.getUserId(), "status", expense.getStatus().toString());
        
        if (!opaClient.authorize(context, "update_expense", resource)) {
            throw new AccessDeniedException("Not authorized to delete items from this expense");
        }
        
        ExpenseItem item = expense.getItems().stream()
            .filter(i -> i.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() -> new ExpenseItemNotFoundException(itemId));
        
        expense.removeItem(item);
        expenseRepository.save(expense);
    }
    
    @Override
    public Expense submitExpense(UUID id, SecurityContext context) {
        Expense expense = expenseRepository.findByIdAndTenantId(id, context.getTenantId())
            .orElseThrow(() -> new ExpenseNotFoundException(id));

        if (expense.getStatus() != ExpenseStatus.DRAFT) {
            throw new InvalidExpenseStatusException("Can only submit expenses in DRAFT status");
        }

        if (expense.getItems().isEmpty()) {
            throw new IllegalStateException("Cannot submit expense without any items");
        }

        Map<String, Object> resource = Map.of("type", "expense", "id", expense.getId().toString(),
            "tenant_id", expense.getTenantId(), "user_id", expense.getUserId(), "status", expense.getStatus().toString());

        if (!opaClient.authorize(context, "submit_expense", resource)) {
            throw new AccessDeniedException("Not authorized to submit this expense");
        }

        // Budget enforcement: if the expense is linked to a travel authorization,
        // its total must not exceed the pre-approved budget.
        if (expense.getBookingId() != null) {
            travelServiceClient.getBookingBudget(expense.getBookingId()).ifPresent(bb -> {
                if (expense.getTotalAmount() != null &&
                        expense.getTotalAmount().compareTo(bb.budget()) > 0) {
                    throw new BudgetExceededException(bb.budget(), expense.getTotalAmount(), bb.budgetCurrency());
                }
            });
        }

        expense.setStatus(ExpenseStatus.SUBMITTED);
        expense.setSubmissionDate(LocalDateTime.now());
        expense.setUpdatedBy(context.getUserId());

        Expense submitted = expenseRepository.save(expense);
        auditService.record(submitted.getId(), "SUBMIT",
            Map.of("itemCount", submitted.getItems().size(),
                   "totalAmount", submitted.getTotalAmount() != null ? submitted.getTotalAmount().toPlainString() : "0"),
            context);
        return submitted;
    }
    
    @Override
    public Expense approveExpense(UUID id, String comments, SecurityContext context) {
        Expense expense = expenseRepository.findByIdAndTenantId(id, context.getTenantId())
            .orElseThrow(() -> new ExpenseNotFoundException(id));
        
        if (expense.getStatus() != ExpenseStatus.SUBMITTED) {
            throw new InvalidExpenseStatusException("Can only approve expenses in SUBMITTED status");
        }
        
        Map<String, Object> resource = Map.of("type", "expense", "id", expense.getId().toString(),
            "tenant_id", expense.getTenantId(), "user_id", expense.getUserId(), "status", expense.getStatus().toString());
        
        if (!opaClient.authorize(context, "approve_expense", resource)) {
            throw new AccessDeniedException("Not authorized to approve this expense");
        }
        
        expense.setStatus(ExpenseStatus.APPROVED);
        expense.setApprovalDate(LocalDateTime.now());
        expense.setApproverId(context.getUserId());
        expense.setUpdatedBy(context.getUserId());

        Expense approved = expenseRepository.save(expense);
        auditService.record(approved.getId(), "APPROVE",
            comments != null ? Map.of("comments", comments) : Map.of(), context);
        return approved;
    }

    @Override
    public Expense rejectExpense(UUID id, String comments, SecurityContext context) {
        Expense expense = expenseRepository.findByIdAndTenantId(id, context.getTenantId())
            .orElseThrow(() -> new ExpenseNotFoundException(id));
        
        if (expense.getStatus() != ExpenseStatus.SUBMITTED) {
            throw new InvalidExpenseStatusException("Can only reject expenses in SUBMITTED status");
        }
        
        Map<String, Object> resource = Map.of("type", "expense", "id", expense.getId().toString(),
            "tenant_id", expense.getTenantId(), "user_id", expense.getUserId(), "status", expense.getStatus().toString());
        
        if (!opaClient.authorize(context, "approve_expense", resource)) {
            throw new AccessDeniedException("Not authorized to reject this expense");
        }
        
        expense.setStatus(ExpenseStatus.REJECTED);
        expense.setApprovalDate(LocalDateTime.now());
        expense.setApproverId(context.getUserId());
        expense.setUpdatedBy(context.getUserId());

        Expense rejected = expenseRepository.save(expense);
        auditService.record(rejected.getId(), "REJECT",
            comments != null ? Map.of("comments", comments) : Map.of(), context);
        return rejected;
    }
}