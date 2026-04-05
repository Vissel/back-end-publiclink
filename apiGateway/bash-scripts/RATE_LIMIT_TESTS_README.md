# Rate Limiter Test Scripts

This directory contains bash scripts to test the rate limiter functionality implemented in the Spring Cloud Gateway.

## Prerequisites

- The API Gateway application must be running on `http://localhost:8080`
- Rate limiting must be enabled in the configuration
- curl must be installed on the system

## Test Scripts

### 1. `test_normal_requests.sh`
Tests normal operation with a few requests to verify rate limit headers are present.

### 2. `test_rate_limit_exceeded.sh`
Makes rapid requests to trigger rate limiting and verify 429 responses.

### 3. `test_excluded_paths.sh`
Tests paths that should bypass rate limiting (configured in `excludePaths`).

### 4. `test_different_policies.sh`
Tests different endpoints that may have different rate limit policies.

### 5. `test_different_keys.sh`
Tests rate limiting with different client keys (simulated via headers).

### 6. `run_all_rate_limit_tests.sh`
Runs all test scripts sequentially.

## Usage

```bash
# Run individual tests
./test_normal_requests.sh
./test_rate_limit_exceeded.sh
./test_excluded_paths.sh
./test_different_policies.sh
./test_different_keys.sh

# Run all tests
./run_all_rate_limit_tests.sh
```

## Expected Headers

The rate limiter adds the following headers to responses:

- `X-Rate-Limit-Limit`: Maximum requests allowed
- `X-Rate-Limit-Remaining`: Remaining requests in current window
- `X-Rate-Limit-Reset`: Timestamp when the limit resets
- `Retry-After`: Seconds to wait before retrying (when rate limited)

## Response Codes

- `200`: Request allowed
- `429`: Rate limit exceeded

## Configuration

Rate limiting behavior is controlled by `RateLimitProperties` in the application configuration. Update the properties to modify:

- Rate limit policies per path pattern
- Excluded paths
- Key resolution strategy
- Redis connection (if using Redis-based rate limiting)
