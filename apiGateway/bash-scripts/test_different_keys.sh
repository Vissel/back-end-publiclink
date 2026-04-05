#!/bin/bash

# Test script for different keys (e.g., different users/IPs)
# This script simulates requests from different clients/keys

echo "Testing rate limiting with different keys..."

BASE_URL="http://localhost:8080"
ENDPOINT="/api/test"
USERS=("user1" "user2" "user3")
REQUESTS_PER_USER=6

for user in "${USERS[@]}"; do
    echo "Testing with user/key: $user"
    for i in $(seq 1 $REQUESTS_PER_USER); do
        echo "Request $i from $user:"
        curl -s -w "Status: %{http_code}, Remaining: %{header:X-Rate-Limit-Remaining}\n" \
             -H "Content-Type: application/json" \
             -H "Authorization: Bearer token-for-$user" \
             -H "X-Forwarded-For: 192.168.1.$((10 + RANDOM % 100))" \
             -X GET "$BASE_URL$ENDPOINT"
        sleep 0.2
    done
    echo "---"
done

echo "Different keys test completed."
