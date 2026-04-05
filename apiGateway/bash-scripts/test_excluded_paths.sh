#!/bin/bash

# Test script for excluded paths
# This script tests paths that should bypass rate limiting

echo "Testing excluded paths (should bypass rate limiting)..."

BASE_URL="http://localhost:8080"
EXCLUDED_ENDPOINTS=("/login" "/public/status" "/api/v1/server-auth/public-key")
REQUESTS_PER_ENDPOINT=5

for endpoint in "${EXCLUDED_ENDPOINTS[@]}"; do
    echo "Testing excluded endpoint: $endpoint"
    for i in $(seq 1 $REQUESTS_PER_ENDPOINT); do
        echo "Request $i to $endpoint:"
        curl -s -w "Status: %{http_code}, Remaining: %{header:X-Rate-Limit-Remaining}\n" \
             -H "Content-Type: application/json" \
             -X GET "$BASE_URL$endpoint"
        sleep 0.2
    done
    echo "---"
done

echo "Excluded paths test completed."
