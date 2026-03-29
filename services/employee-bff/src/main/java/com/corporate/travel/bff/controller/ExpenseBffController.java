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
@Tag(name = "Expense BFF", description = "Expense operations with automatic delegation token injection")
public class ExpenseBffController {

    private final ExpenseServiceClient expenseServiceClient;
    private final DelegationContextService delegationContextService;

    @GetMapping
    @Operation(summary = "List expenses",
        description = "Lists expenses for the current user, or the delegation subject if delegation is active.")
    public ResponseEntity<JsonNode> getExpenses(
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        String token = resolveToken(jwt, session);
        return ResponseEntity.ok(expenseServiceClient.getExpenses(token));
    }

    @PostMapping
    @Operation(summary = "Create expense",
        description = "Creates an expense. If delegation is active, uses the delegation token " +
                      "(sub=subject, act.sub=actor) so the expense is attributed to the subject.")
    public ResponseEntity<JsonNode> createExpense(
            @RequestBody JsonNode requestBody,
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        String token = resolveToken(jwt, session);
        return ResponseEntity.ok(expenseServiceClient.createExpense(requestBody, token));
    }

    @GetMapping("/{expenseId}")
    @Operation(summary = "Get expense by ID")
    public ResponseEntity<JsonNode> getExpense(
            @PathVariable String expenseId,
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        String token = resolveToken(jwt, session);
        return ResponseEntity.ok(expenseServiceClient.getExpense(expenseId, token));
    }

    /**
     * If delegation is active and not expired, use the delegation token.
     * Otherwise fall back to the caller's own token.
     */
    private String resolveToken(Jwt jwt, HttpSession session) {
        Optional<DelegationContext> context = delegationContextService.getActiveContext(session);
        return context.map(DelegationContext::getDelegationToken)
                      .orElse(jwt.getTokenValue());
    }
}
