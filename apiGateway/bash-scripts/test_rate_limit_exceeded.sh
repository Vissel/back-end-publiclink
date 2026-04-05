#!/bin/bash

# Test script for rate limit exceeded scenario
# This script makes many rapid requests to trigger rate limiting

echo "Testing rate limit exceeded scenario..."

BASE_URL="http://localhost:8080"
ENDPOINT="/api/test"
REQUESTS=15  # More than typical rate limit

echo "Making $REQUESTS rapid requests to $BASE_URL$ENDPOINT"

for i in $(seq 1 $REQUESTS); do
    echo "Request $i:"
    response=$(curl -s -w "Status: %{http_code}, Remaining: %{header:X-Rate-Limit-Remaining}, Retry-After: %{header:Retry-After}\n" \
               -H "Content-Type: application/json" \
               -X GET "$BASE_URL$ENDPOINT")

    echo "$response"

    # Check if rate limited (429)
    if echo "$response" | grep -q "Status: 429"; then
        echo "Rate limit exceeded! Stopping further requests."
        break
    fi

    # Small delay to avoid overwhelming but still rapid
    sleep 0.1
done

echo "Rate limit test completed."
