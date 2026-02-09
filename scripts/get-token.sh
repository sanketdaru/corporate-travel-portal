#!/bin/bash

# Get JWT token from Keycloak for testing
# Usage: ./scripts/get-token.sh [username] [password]

USERNAME=${1:-alice.employee}
PASSWORD=${2:-password123}

echo "🔐 Getting access token for user: $USERNAME"
echo ""

RESPONSE=$(curl -s -X POST "http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=employee-portal" \
  -d "username=$USERNAME" \
  -d "password=$PASSWORD" \
  -d "grant_type=password" \
  -d "scope=openid")

if [ $? -ne 0 ]; then
    echo "❌ Failed to get token. Is Keycloak running?"
    exit 1
fi

ACCESS_TOKEN=$(echo $RESPONSE | jq -r '.access_token')

if [ "$ACCESS_TOKEN" == "null" ] || [ -z "$ACCESS_TOKEN" ]; then
    echo "❌ Failed to extract access token"
    echo "Response: $RESPONSE"
    exit 1
fi

echo "✅ Access Token obtained successfully!"
echo ""
echo "Token (first 50 chars): ${ACCESS_TOKEN:0:50}..."
echo ""
echo "📋 Full token copied to clipboard (if available)"
echo ""

# Try to copy to clipboard (works on Linux with xclip)
if command -v xclip > /dev/null 2>&1; then
    echo $ACCESS_TOKEN | xclip -selection clipboard
    echo "✅ Token copied to clipboard (xclip)"
elif command -v pbcopy > /dev/null 2>&1; then
    echo $ACCESS_TOKEN | pbcopy
    echo "✅ Token copied to clipboard (pbcopy)"
fi

echo ""
echo "🧪 Test API call:"
echo "curl -H \"Authorization: Bearer \$TOKEN\" http://localhost:8000/api/bookings"
echo ""

# Save to temp file
echo $ACCESS_TOKEN > /tmp/keycloak-token.txt
echo "💾 Token saved to: /tmp/keycloak-token.txt"
echo ""

# Decode and display token claims (if jq is available)
if command -v jq > /dev/null 2>&1; then
    echo "📄 Token Claims:"
    echo $ACCESS_TOKEN | cut -d'.' -f2 | base64 -d 2>/dev/null | jq '.' || true
fi

echo ""
echo "🔗 Use in curl:"
echo "export TOKEN=\"$ACCESS_TOKEN\""
echo "curl -H \"Authorization: Bearer \$TOKEN\" http://localhost:8000/api/bookings"
