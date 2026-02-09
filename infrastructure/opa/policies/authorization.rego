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

# Expense Authorization

# Allow user to view their own expenses
allow if {
    input.action == "view_expense"
    is_same_tenant
    is_resource_owner
}

# Allow user to create expense
allow if {
    input.action == "create_expense"
    is_same_tenant
    has_role("employee")
}

# Allow user to submit expense for approval
allow if {
    input.action == "submit_expense"
    is_same_tenant
    is_resource_owner
}

# Allow manager to view team expenses
allow if {
    input.action == "view_expense"
    is_same_tenant
    is_manager_of_user
}

# Allow manager to approve expenses
allow if {
    input.action == "approve_expense"
    is_same_tenant
    has_role("manager")
    is_manager_of_user
    input.resource.status == "SUBMITTED"
}

# Allow manager to approve on behalf of another manager (with delegation)
allow if {
    input.action == "approve_expense"
    is_same_tenant
    has_active_delegation
    input.consent.valid == true
    "approve_expenses" in input.consent.scopes
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
