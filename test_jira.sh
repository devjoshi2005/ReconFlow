#!/bin/bash

# ReconFlow - Jira Connectivity Test Script
# This script tests your Jira API integration by creating a mock "Reconciliation Exception" task.

echo "========================================"
echo " ReconFlow - Jira API Integration Test"
echo "========================================"
echo ""
echo "Please enter your Atlassian Jira details:"

read -p "Jira Base URL (e.g., https://your-domain.atlassian.net): " JIRA_URL
read -p "Jira Email Address: " JIRA_EMAIL
read -p "Jira API Token (Create at https://id.atlassian.com/manage-profile/security/api-tokens): " JIRA_TOKEN
read -p "Jira Project Key (e.g., RECON): " JIRA_PROJECT_KEY

# Basic validation
if [[ -z "$JIRA_URL" || -z "$JIRA_EMAIL" || -z "$JIRA_TOKEN" || -z "$JIRA_PROJECT_KEY" ]]; then
    echo "Error: All fields are required to test the connection."
    exit 1
fi

echo ""
echo "Testing connection and creating a mock Exception Ticket..."

PAYLOAD=$(cat <<EOF
{
  "fields": {
    "project": {
      "key": "$JIRA_PROJECT_KEY"
    },
    "summary": "ReconFlow Test: AMOUNT_MISMATCH on ORD-TEST-123",
    "description": "This is a test ticket generated from the ReconFlow Jira Connectivity Script.\nDiscrepancy Amount: 50.00 INR",
    "issuetype": {
      "name": "Task"
    }
  }
}
EOF
)

# Perform the cURL request
RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST \
  -u "$JIRA_EMAIL:$JIRA_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD" \
  "$JIRA_URL/rest/api/2/issue")

# Extract Body and Status Code
HTTP_STATUS=$(echo "$RESPONSE" | grep "HTTP_STATUS:" | awk -F: '{print $2}')
BODY=$(echo "$RESPONSE" | sed -e 's/HTTP_STATUS:.*//g')

echo "----------------------------------------"
if [[ "$HTTP_STATUS" == "201" ]]; then
    echo "✅ SUCCESS! Jira integration is working perfectly."
    echo "Response: $BODY"
else
    echo "❌ FAILED. Jira API returned HTTP Status $HTTP_STATUS."
    echo "Response: $BODY"
    echo "Please check your URL, Email, Token, and ensure the Project Key exists and supports 'Task' issue types."
fi
echo "========================================"
