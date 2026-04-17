package com.corporate.travel.expense.controller;

import com.corporate.travel.expense.model.entity.Expense;
import com.corporate.travel.expense.model.entity.ExpenseAudit;
import com.corporate.travel.expense.model.entity.ExpenseItem;
import com.corporate.travel.expense.service.ExpenseAuditService;
import com.corporate.travel.expense.service.ExpenseService;
import com.corporate.travel.models.ExpenseStatus;
import com.corporate.travel.security.JwtAuthenticationConverter;
import com.corporate.travel.security.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Expense operations
 */
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Expenses", description = "Expense report and item management API")
@SecurityRequirement(name = "bearerAuth")
public class ExpenseController {
    
    private final ExpenseService expenseService;
    private final ExpenseAuditService expenseAuditService;
    
    @Operation(
        summary = "Create a new expense report",
        description = "Creates a new expense report for the authenticated user or on behalf of another user (delegation)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Expense created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid expense data", content = @Content),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content)
    })
    @PostMapping
    public ResponseEntity<Expense> createExpense(
            @Valid @RequestBody Expense expense,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        log.info("Creating expense for user: {}, isDelegated: {}", context.getUserId(), context.isDelegated());
        
        Expense created = expenseService.createExpense(expense, context);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @Operation(
        summary = "Get all expenses",
        description = "Retrieves all expenses for the authenticated user with multi-tenant isolation"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Expenses retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content)
    })
    @GetMapping
    public ResponseEntity<List<Expense>> getUserExpenses(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        log.debug("Fetching expenses for user: {}, isDelegated: {}", context.getUserId(), context.isDelegated());

        List<Expense> expenses = expenseService.getUserExpenses(context);
        return ResponseEntity.ok(expenses);
    }

    /** GET /api/expenses/all — admin role only, returns all tenant expenses */
    @GetMapping("/all")
    public ResponseEntity<List<Expense>> getAllTenantExpenses(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        return ResponseEntity.ok(expenseService.getTenantExpenses(context));
    }
    
    @Operation(
        summary = "Get expense by ID",
        description = "Retrieves a specific expense report with its items. Authorization via OPA ensures proper access control."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Expense found"),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Expense not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<Expense> getExpense(
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        log.debug("Fetching expense {} for user: {}, isDelegated: {}", id, context.getUserId(), context.isDelegated());
        
        Expense expense = expenseService.getExpense(id, context);
        return ResponseEntity.ok(expense);
    }
    
    @Operation(
        summary = "Update expense report",
        description = "Updates an expense report (only in DRAFT status)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Expense updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status for update", content = @Content),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Expense not found", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<Expense> updateExpense(
            @PathVariable UUID id,
            @Valid @RequestBody Expense expenseUpdate,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        log.info("Updating expense {} by user: {}, isDelegated: {}", id, context.getUserId(), context.isDelegated());
        
        Expense updated = expenseService.updateExpense(id, expenseUpdate, context);
        return ResponseEntity.ok(updated);
    }
    
    @Operation(
        summary = "Get expense audit trail",
        description = "Returns all audit records for an expense, ordered by most recent first (ADR-011)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Audit trail retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Expense not found", content = @Content)
    })
    @GetMapping("/{id}/audit")
    public ResponseEntity<List<ExpenseAudit>> getExpenseAudit(
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        expenseService.getExpense(id, context);
        List<ExpenseAudit> trail = expenseAuditService.getAuditTrail(id, context);
        return ResponseEntity.ok(trail);
    }

    @Operation(
        summary = "Delete expense report",
        description = "Deletes an expense report (only in DRAFT status)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Expense deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status for deletion", content = @Content),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Expense not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        log.info("Deleting expense {} by user: {}", id, context.getUserId());

        expenseService.deleteExpense(id, context);
        return ResponseEntity.noContent().build();
    }
    
    // ===== Expense Item Operations =====
    
    @Operation(
        summary = "Add expense item",
        description = "Adds a line item to an expense report (only in DRAFT status)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Item added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status or data", content = @Content),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Expense not found", content = @Content)
    })
    @PostMapping("/{expenseId}/items")
    public ResponseEntity<ExpenseItem> addExpenseItem(
            @PathVariable UUID expenseId,
            @Valid @RequestBody ExpenseItem item,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        log.info("Adding item to expense {} by user: {}, isDelegated: {}", expenseId, context.getUserId(), context.isDelegated());
        
        ExpenseItem created = expenseService.addExpenseItem(expenseId, item, context);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @Operation(
        summary = "Update expense item",
        description = "Updates a line item in an expense report (only when expense is DRAFT)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Item updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status for update", content = @Content),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Expense or item not found", content = @Content)
    })
    @PutMapping("/{expenseId}/items/{itemId}")
    public ResponseEntity<ExpenseItem> updateExpenseItem(
            @PathVariable UUID expenseId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ExpenseItem itemUpdate,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        log.info("Updating item {} in expense {} by user: {}", itemId, expenseId, context.getUserId());
        
        ExpenseItem updated = expenseService.updateExpenseItem(expenseId, itemId, itemUpdate, context);
        return ResponseEntity.ok(updated);
    }
    
    @Operation(
        summary = "Delete expense item",
        description = "Deletes a line item from an expense report (only when expense is DRAFT)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Item deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status for deletion", content = @Content),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Expense or item not found", content = @Content)
    })
    @DeleteMapping("/{expenseId}/items/{itemId}")
    public ResponseEntity<Void> deleteExpenseItem(
            @PathVariable UUID expenseId,
            @PathVariable UUID itemId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        log.info("Deleting item {} from expense {} by user: {}", itemId, expenseId, context.getUserId());
        
        expenseService.deleteExpenseItem(expenseId, itemId, context);
        return ResponseEntity.noContent().build();
    }
    
    // ===== Workflow Operations =====
    
    @Operation(
        summary = "Submit expense for approval",
        description = "Submits an expense report for approval (DRAFT → SUBMITTED)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Expense submitted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status or no items", content = @Content),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Expense not found", content = @Content)
    })
    @PostMapping("/{id}/submit")
    public ResponseEntity<Expense> submitExpense(
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        log.info("Submitting expense {} by user: {}, isDelegated: {}", id, context.getUserId(), context.isDelegated());
        
        Expense submitted = expenseService.submitExpense(id, context);
        return ResponseEntity.ok(submitted);
    }
    
    @Operation(
        summary = "Approve expense",
        description = "Approves a submitted expense report (SUBMITTED → APPROVED). Requires manager role."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Expense approved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status for approval", content = @Content),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Expense not found", content = @Content)
    })
    @PostMapping("/{id}/approve")
    public ResponseEntity<Expense> approveExpense(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        
        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt);
        log.info("Approving expense {} by user: {}", id, context.getUserId());
        
        String comments = request != null ? request.get("comments") : null;
        Expense approved = expenseService.approveExpense(id, comments, context);
        return ResponseEntity.ok(approved);
    }
    
    @Operation(
        summary = "Reject expense",
        description = "Rejects a submitted expense report (SUBMITTED → REJECTED). Requires manager role."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Expense rejected successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status for rejection", content = @Content),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Expense not found", content = @Content)
    })
    @PostMapping("/{id}/reject")
    public ResponseEntity<Expense> rejectExpense(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        
        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt);
        log.info("Rejecting expense {} by user: {}", id, context.getUserId());
        
        String comments = request != null ? request.get("comments") : null;
        Expense rejected = expenseService.rejectExpense(id, comments, context);
        return ResponseEntity.ok(rejected);
    }
}