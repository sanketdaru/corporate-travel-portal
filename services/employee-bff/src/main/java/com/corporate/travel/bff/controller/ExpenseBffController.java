package com.corporate.travel.bff.controller;

import com.corporate.travel.bff.client.ExpenseServiceClient;
import com.corporate.travel.bff.model.DelegationContext;
import com.corporate.travel.bff.service.DelegationContextService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/bff/expenses")
@RequiredArgsConstructor
@Tag(name = "Expense BFF", description = "Expense operations with automatic delegation context injection")
public class ExpenseBffController {

    private final ExpenseServiceClient expenseServiceClient;
    private final DelegationContextService delegationContextService;

    @GetMapping
    @Operation(summary = "List expenses",
        description = "Lists expenses for the current user, or the delegation subject if delegation is active. " +
                      "When delegation is active, X-Delegated-Subject, X-Delegation-Id, and X-Actor-Token " +
                      "headers are threaded to expense-service (ADR-004, ADR-018).")
    public ResponseEntity<JsonNode> getExpenses(
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        Optional<DelegationContext> ctx = resolveDelegationContext(session);
        String token = ctx.map(DelegationContext::getDelegationToken).orElse(jwt.getTokenValue());
        return ResponseEntity.ok(expenseServiceClient.getExpenses(token, ctx));
    }

    @PostMapping
    @Operation(summary = "Create expense",
        description = "Creates an expense. If delegation is active, the audience-scoped delegation token " +
                      "is used (sub=actor) and X-Delegated-Subject identifies the human principal, " +
                      "so the expense is attributed to the subject in audit records (ADR-011).")
    public ResponseEntity<JsonNode> createExpense(
            @RequestBody JsonNode requestBody,
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        Optional<DelegationContext> ctx = resolveDelegationContext(session);
        String token = ctx.map(DelegationContext::getDelegationToken).orElse(jwt.getTokenValue());
        return ResponseEntity.ok(expenseServiceClient.createExpense(requestBody, token, ctx));
    }

    @GetMapping("/{expenseId}")
    @Operation(summary = "Get expense by ID")
    public ResponseEntity<JsonNode> getExpense(
            @PathVariable String expenseId,
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        Optional<DelegationContext> ctx = resolveDelegationContext(session);
        String token = ctx.map(DelegationContext::getDelegationToken).orElse(jwt.getTokenValue());
        return ResponseEntity.ok(expenseServiceClient.getExpense(expenseId, token, ctx));
    }

    /**
     * Returns the active, non-expired delegation context from the session if one exists.
     * The full context — not just a token string — is required so that delegation headers
     * (X-Delegated-Subject, X-Delegation-Id, X-Actor-Token) can be threaded on downstream calls.
     */
    private Optional<DelegationContext> resolveDelegationContext(HttpSession session) {
        return delegationContextService.getActiveContext(session);
    }
}
