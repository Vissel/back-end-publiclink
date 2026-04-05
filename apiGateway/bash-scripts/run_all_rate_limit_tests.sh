#!/bin/bash

# Comprehensive test script for rate limiter
# Runs all rate limiter tests

echo "Starting comprehensive rate limiter tests..."
echo "=========================================="

# Make scripts executable
chmod +x test_*.sh

# Run all tests
echo "1. Testing normal requests..."
./test_normal_requests.sh
echo ""

echo "2. Testing rate limit exceeded..."
./test_rate_limit_exceeded.sh
echo ""

echo "3. Testing excluded paths..."
./test_excluded_paths.sh
echo ""

echo "4. Testing different policies..."
./test_different_policies.sh
echo ""

echo "5. Testing different keys..."
./test_different_keys.sh
echo ""

echo "=========================================="
echo "All rate limiter tests completed!"
echo ""
echo "Summary of expected behaviors:"
echo "- Normal requests should succeed with rate limit headers"
echo "- Exceeded limits should return 429 with Retry-After header"
echo "- Excluded paths should not have rate limiting applied"
echo "- Different policies should show different limits per endpoint"
echo "- Different keys should have separate rate limit counters"
