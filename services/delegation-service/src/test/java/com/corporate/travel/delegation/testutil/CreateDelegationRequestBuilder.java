package com.corporate.travel.delegation.testutil;

import com.corporate.travel.delegation.model.dto.CreateDelegationRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating CreateDelegationRequest test data
 * Used for testing delegation creation operations
 */
public class CreateDelegationRequestBuilder {
    
    private String delegateId = "dave.assistant";
    private String purpose = "book_travel";
    private List<String> scopes = new ArrayList<>(List.of("view_bookings", "create_bookings"));
    private LocalDateTime expiresAt = null;
    
    private CreateDelegationRequestBuilder() {
        // Private constructor to enforce factory method usage
    }
    
    /**
     * Factory method to create a new builder instance
     */
    public static CreateDelegationRequestBuilder aRequest() {
        return new CreateDelegationRequestBuilder();
    }
    
    /**
     * Create a request with all default valid data
     */
    public static CreateDelegationRequest aValidRequest() {
        return aRequest().build();
    }
    
    public CreateDelegationRequestBuilder withDelegateId(String delegateId) {
        this.delegateId = delegateId;
        return this;
    }
    
    public CreateDelegationRequestBuilder withPurpose(String purpose) {
        this.purpose = purpose;
        return this;
    }
    
    public CreateDelegationRequestBuilder withScopes(List<String> scopes) {
        this.scopes = new ArrayList<>(scopes);
        return this;
    }
    
    public CreateDelegationRequestBuilder withSingleScope(String scope) {
        this.scopes = new ArrayList<>(List.of(scope));
        return this;
    }
    
    public CreateDelegationRequestBuilder withEmptyScopes() {
        this.scopes = new ArrayList<>();
        return this;
    }
    
    public CreateDelegationRequestBuilder withExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }
    
    public CreateDelegationRequestBuilder withFutureExpiration(int days) {
        this.expiresAt = LocalDateTime.now().plusDays(days);
        return this;
    }
    
    public CreateDelegationRequestBuilder withPastExpiration() {
        this.expiresAt = LocalDateTime.now().minusDays(1);
        return this;
    }
    
    /**
     * Configure for booking delegation
     */
    public CreateDelegationRequestBuilder forBooking() {
        this.purpose = "book_travel";
        this.scopes = new ArrayList<>(List.of("view_bookings", "create_bookings", "update_bookings"));
        return this;
    }
    
    /**
     * Configure for expense delegation
     */
    public CreateDelegationRequestBuilder forExpense() {
        this.purpose = "manage_expenses";
        this.scopes = new ArrayList<>(List.of("view_expenses", "create_expenses", "submit_expenses"));
        return this;
    }
    
    /**
     * Configure for approval delegation
     */
    public CreateDelegationRequestBuilder forApproval() {
        this.purpose = "approve_requests";
        this.scopes = new ArrayList<>(List.of("view_approvals", "approve_expenses", "approve_bookings"));
        return this;
    }
    
    /**
     * Build the CreateDelegationRequest DTO
     */
    public CreateDelegationRequest build() {
        return CreateDelegationRequest.builder()
                .delegateId(delegateId)
                .purpose(purpose)
                .scopes(scopes)
                .expiresAt(expiresAt)
                .build();
    }
}