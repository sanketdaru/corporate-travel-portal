#!/bin/bash

# Test OPA authorization policy
# Usage: ./scripts/test-opa-policy.sh

echo "🔍 Testing OPA Authorization Policies"
echo ""

# Test 1: User can view their own booking
echo "Test 1: User viewing their own booking"
RESULT=$(curl -s -X POST http://localhost:8181/v1/data/corporate/travel/authorization/allow \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "user": {
        "user_id": "alice",
        "tenant_id": "tenant-a",
        "roles": ["employee"]
      },
      "action": "view_booking",
      "resource": {
        "type": "booking",
        "tenant_id": "tenant-a",
        "user_id": "alice"
      },
      "delegation": {
        "active": false
      },
      "consent": {
        "valid": false,
        "scopes": []
      }
    }
  }')

ALLOWED=$(echo $RESULT | jq -r '.result')
if [ "$ALLOWED" == "true" ]; then
    echo "✅ PASS: User can view their own booking"
else
    echo "❌ FAIL: User should be able to view their own booking"
    echo "Response: $RESULT"
fi
echo ""

# Test 2: User cannot view booking from different tenant
echo "Test 2: User attempting to view booking from different tenant"
RESULT=$(curl -s -X POST http://localhost:8181/v1/data/corporate/travel/authorization/allow \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "user": {
        "user_id": "alice",
        "tenant_id": "tenant-a",
        "roles": ["employee"]
      },
      "action": "view_booking",
      "resource": {
        "type": "booking",
        "tenant_id": "tenant-b",
        "user_id": "eve"
      },
      "delegation": {
        "active": false
      },
      "consent": {
        "valid": false,
        "scopes": []
      }
    }
  }')

ALLOWED=$(echo $RESULT | jq -r '.result')
if [ "$ALLOWED" == "false" ]; then
    echo "✅ PASS: Cross-tenant access denied"
else
    echo "❌ FAIL: Cross-tenant access should be denied"
    echo "Response: $RESULT"
fi
echo ""

# Test 3: Manager can approve expenses
echo "Test 3: Manager approving team expense"
RESULT=$(curl -s -X POST http://localhost:8181/v1/data/corporate/travel/authorization/allow \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "user": {
        "user_id": "bob",
        "tenant_id": "tenant-a",
        "roles": ["manager", "employee"]
      },
      "action": "approve_expense",
      "resource": {
        "type": "expense",
        "tenant_id": "tenant-a",
        "user_id": "alice",
        "status": "SUBMITTED",
        "manager_chain": ["bob"]
      },
      "delegation": {
        "active": false
      },
      "consent": {
        "valid": false,
        "scopes": []
      }
    }
  }')

ALLOWED=$(echo $RESULT | jq -r '.result')
if [ "$ALLOWED" == "true" ]; then
    echo "✅ PASS: Manager can approve team expense"
else
    echo "❌ FAIL: Manager should be able to approve team expense"
    echo "Response: $RESULT"
fi
echo ""

# Test 4: Delegated booking with consent
echo "Test 4: Assistant booking travel with delegation"
RESULT=$(curl -s -X POST http://localhost:8181/v1/data/corporate/travel/authorization/allow \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "user": {
        "user_id": "dave",
        "tenant_id": "tenant-a",
        "roles": ["assistant", "employee"]
      },
      "action": "create_booking",
      "resource": {
        "type": "booking",
        "tenant_id": "tenant-a",
        "user_id": "carol"
      },
      "delegation": {
        "active": true,
        "delegate_id": "dave",
        "delegator_id": "carol"
      },
      "consent": {
        "valid": true,
        "scopes": ["book_travel", "view_booking"]
      }
    }
  }')

ALLOWED=$(echo $RESULT | jq -r '.result')
if [ "$ALLOWED" == "true" ]; then
    echo "✅ PASS: Delegated booking with consent allowed"
else
    echo "❌ FAIL: Delegated booking with consent should be allowed"
    echo "Response: $RESULT"
fi
echo ""

# Test 5: Admin can do anything in their tenant
echo "Test 5: Admin accessing resources in their tenant"
RESULT=$(curl -s -X POST http://localhost:8181/v1/data/corporate/travel/authorization/allow \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "user": {
        "user_id": "admin-user",
        "tenant_id": "tenant-a",
        "roles": ["admin"]
      },
      "action": "view_booking",
      "resource": {
        "type": "booking",
        "tenant_id": "tenant-a",
        "user_id": "alice"
      },
      "delegation": {
        "active": false
      },
      "consent": {
        "valid": false,
        "scopes": []
      }
    }
  }')

ALLOWED=$(echo $RESULT | jq -r '.result')
if [ "$ALLOWED" == "true" ]; then
    echo "✅ PASS: Admin can access tenant resources"
else
    echo "❌ FAIL: Admin should be able to access tenant resources"
    echo "Response: $RESULT"
fi
echo ""

echo "✨ OPA Policy Tests Complete!"
echo ""
echo "📝 Note: These tests verify the authorization policies work as expected."
echo "   Run 'docker-compose logs opa' to see detailed policy evaluation logs."
