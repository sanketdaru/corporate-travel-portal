package corporate.travel.authorization

import future.keywords.if
import future.keywords.in

# Default deny
default allow = false

# Helper functions
is_same_tenant if {
    input.user.tenant_id == input.resource.tenant_id
}

has_role(role) if {
    role in input.user.roles
}

is_resource_owner if {
    input.user.user_id == input.resource.user_id
}

has_active_delegation if {
    input.delegation.active == true
    input.delegation.delegator_id == input.resource.user_id
    input.delegation.delegate_id == input.user.user_id
}

is_manager_of_user if {
    has_role("manager")
    input.user.user_id in input.resource.manager_chain
}

# Travel Booking Authorization

# Allow user to view their own bookings
allow if {
    input.action == "view_booking"
    is_same_tenant
    is_resource_owner
}

# Allow delegate to view bookings they created on behalf of another
allow if {
    input.action == "view_booking"
    is_same_tenant
    has_active_delegation
}

# Allow user to create booking for themselves
allow if {
    input.action == "create_booking"
    is_same_tenant
    has_role("employee")
}

# Allow delegate to create booking on behalf of another (with consent)
allow if {
    input.action == "create_booking"
    is_same_tenant
    has_active_delegation
    input.consent.valid == true
    "book_travel" in input.consent.scopes
}

# Allow manager to view team bookings
allow if {
    input.action == "view_booking"
    is_same_tenant
    is_manager_of_user
}

# Allow owner to update their own booking status
allow if {
    input.action == "update_booking"
    is_same_tenant
    is_resource_owner
}

# Allow delegate to update booking status on behalf of another (with consent)
allow if {
    input.action == "update_booking"
    is_same_tenant
    has_active_delegation
    input.consent.valid == true
    "book_travel" in input.consent.scopes
}

# Expense Authorization

# Allow user to create expense
allow if {
    input.action == "create_expense"
    is_same_tenant
    has_role("employee")
}

# Allow user to view their own expenses
allow if {
    input.action == "view_expense"
    is_same_tenant
    is_resource_owner
}

# Allow delegate to view expenses they created on behalf of another
allow if {
    input.action == "view_expense"
    is_same_tenant
    has_active_delegation
}

# Allow manager to view team expenses
allow if {
    input.action == "view_expense"
    is_same_tenant
    is_manager_of_user
}

# Allow user to update/delete their own expenses (DRAFT only)
allow if {
    input.action in ["update_expense", "delete_expense"]
    is_same_tenant
    is_resource_owner
    input.resource.status == "DRAFT"
}

# Allow delegate to update/delete expenses on behalf of another (DRAFT only, with consent)
allow if {
    input.action in ["update_expense", "delete_expense"]
    is_same_tenant
    has_active_delegation
    input.consent.valid == true
    "manage_expenses" in input.consent.scopes
    input.resource.status == "DRAFT"
}

# Allow user to submit their own expense for approval
allow if {
    input.action == "submit_expense"
    is_same_tenant
    is_resource_owner
    input.resource.status == "DRAFT"
}

# Allow manager to approve/reject expenses
allow if {
    input.action in ["approve_expense", "reject_expense"]
    is_same_tenant
    has_role("manager")
    is_manager_of_user
    input.resource.status == "SUBMITTED"
}

# Allow manager to approve/reject on behalf of another manager (with delegation)
allow if {
    input.action in ["approve_expense", "reject_expense"]
    is_same_tenant
    has_active_delegation
    input.consent.valid == true
    "approve_expenses" in input.consent.scopes
    input.resource.status == "SUBMITTED"
}

# Allow admin to perform any expense action within their tenant
allow if {
    input.action in ["view_expense", "update_expense", "delete_expense", "approve_expense", "reject_expense"]
    has_role("admin")
    is_same_tenant
}

# Approval Workflow Authorization

# Allow user to view their pending approvals
allow if {
    input.action == "view_pending_approvals"
    is_same_tenant
    has_role("manager")
}

# Allow user to view workflows they created
allow if {
    input.action == "view_workflow"
    is_same_tenant
    input.resource.requester_id == input.user.user_id
}

# Delegation Authorization

# Allow user to create delegation for themselves
allow if {
    input.action == "create_delegation"
    is_same_tenant
    input.resource.delegator_id == input.user.user_id
}

# Allow user to revoke their own delegations
allow if {
    input.action == "revoke_delegation"
    is_same_tenant
    input.resource.delegator_id == input.user.user_id
}

# Allow user to view their delegations
allow if {
    input.action == "view_delegations"
    is_same_tenant
}

# Consent Authorization

# Allow user to grant consent on their own behalf (they are the grantor)
allow if {
    input.action == "create_consent"
    is_same_tenant
    input.resource.grantor_id == input.user.user_id
}

# Allow user to view, revoke, or audit their own consents
allow if {
    input.action in ["view_consent", "revoke_consent", "view_consent_audit"]
    is_same_tenant
    input.resource.grantor_id == input.user.user_id
}

# Allow grantee to view consents granted to them
allow if {
    input.action == "view_consent"
    is_same_tenant
    input.resource.grantee_id == input.user.user_id
}

# Allow any authenticated user in the same tenant to list and validate consents
allow if {
    input.action in ["list_my_consents", "list_consents_to_me", "validate_consent"]
    is_same_tenant
}

# Admin Authorization

# Allow admin to perform any action within their tenant
allow if {
    has_role("admin")
    is_same_tenant
}

# Audit logging (always allow but log)
audit_entry = {
    "timestamp": time.now_ns(),
    "user_id": input.user.user_id,
    "tenant_id": input.user.tenant_id,
    "action": input.action,
    "resource_type": input.resource.type,
    "resource_id": input.resource.id,
    "decision": allow,
    "actor_id": input.delegation.delegate_id,
    "subject_id": input.delegation.delegator_id
} if {
    input.delegation.active == true
}

audit_entry = {
    "timestamp": time.now_ns(),
    "user_id": input.user.user_id,
    "tenant_id": input.user.tenant_id,
    "action": input.action,
    "resource_type": input.resource.type,
    "resource_id": input.resource.id,
    "decision": allow
} if {
    not input.delegation.active
}
