#!/bin/bash

# Test script for different rate limit policies
# This script tests different endpoints that may have different rate limit policies

echo "Testing different rate limit policies..."

BASE_URL="http://localhost:8080"
ENDPOINTS=("/api/admin/data" "/api/user/profile" "/api/public/info")
REQUESTS_PER_ENDPOINT=8

for endpoint in "${ENDPOINTS[@]}"; do
    echo "Testing endpoint: $endpoint (may have different policy)"
    for i in $(seq 1 $REQUESTS_PER_ENDPOINT); do
        echo "Request $i to $endpoint:"
        curl -s -w "Status: %{http_code}, Limit: %{header:X-Rate-Limit-Limit}, Remaining: %{header:X-Rate-Limit-Remaining}\n" \
             -H "Content-Type: application/json" \
             -X GET "$BASE_URL$endpoint"
        sleep 0.3
    done
    echo "---"
done

echo "Different policies test completed."
