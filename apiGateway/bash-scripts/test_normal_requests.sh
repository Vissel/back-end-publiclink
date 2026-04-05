#!/bin/bash

# Test script for normal rate limiter behavior
# This script makes a few requests to test normal operation

echo "Testing normal rate limiter behavior..."

BASE_URL="http://localhost:8080"
ENDPOINT="/api/test"

echo "Making 3 normal requests to $BASE_URL$ENDPOINT"

for i in {1..3}; do
    echo "Request $i:"
    curl -s -w "\nStatus: %{http_code}\nRate Limit: %{header:X-Rate-Limit-Limit}\nRemaining: %{header:X-Rate-Limit-Remaining}\nReset: %{header:X-Rate-Limit-Reset}\n\n" \
         -H "Content-Type: application/json" \
         -X GET "$BASE_URL$ENDPOINT"
    sleep 1
done

echo "Normal requests test completed."
