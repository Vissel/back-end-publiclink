# Production-Grade Refresh Token Mechanism Implementation

## Overview
This document describes the banking-grade refresh token implementation following Ant Group financial services security standards for the VietQrApplication API Gateway.

---

## Security Architecture

### Core Security Features

1. **Token Rotation (One-Time Use Refresh Tokens)**
   - Each refresh token can only be used once
   - Old token is deleted before issuing new token pair
   - Prevents replay attacks and token theft

2. **Dual Validation Layer**
   - JWT structural validation (signature, expiration, token type)
   - Redis storage validation (token existence, user context)
   - Blacklist check for compromised tokens

3. **Atomic Operations**
   - Delete old refresh token before generating new ones
   - Prevents race conditions in concurrent refresh requests
   - Ensures consistency between JWT and Redis state

4. **Token Blacklisting**
   - Supports immediate token revocation
   - Used for logout and security breach scenarios
   - TTL matches original token expiration

5. **Comprehensive Audit Trail**
   - Unique trace ID for every operation
   - Full logging of token lifecycle events
   - Security breach detection and alerting

---

## Implementation Components

### 1. Request/Response DTOs

#### RefreshTokenRequest
```java
@Data
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
```

#### RefreshTokenResponse
```java
@Data
@Builder
public class RefreshTokenResponse {
    private String accessToken;
    private String refreshToken;
    private String username;
    private List<String> roles;
    private Date expiresAt;
    private String message;
}
```

### 2. Redis Storage Service (RefreshTokenStorageService)

**Key Features:**
- Stores refresh tokens with user context (username + roles)
- Implements TTL-based automatic expiration
- Supports token blacklisting
- Atomic delete operations for rotation

**Redis Key Structure:**
- Refresh tokens: `refresh_token:{token}`
- Blacklist: `token_blacklist:{token}`

**Methods:**
- `storeRefreshToken()` - Store with TTL
- `validateAndGetRefreshToken()` - Validate and retrieve
- `deleteRefreshToken()` - Atomic deletion for rotation
- `blacklistToken()` - Revoke compromised tokens
- `isTokenBlacklisted()` - Check revocation status

### 3. JWT Token Producer Enhancements

**New Methods:**
- `validateRefreshToken()` - Validates token type + expiration
- `extractUsernameFromRefreshToken()` - Secure username extraction with type checking

**Security Checks:**
- Token type must be "refresh" (not "access")
- Expiration validation
- Subject claim presence

### 4. Refresh Token Service (RefreshTokenService)

**Refresh Flow (7 Steps):**
1. Validate request (Jakarta Validation)
2. Validate JWT structure and type
3. Check blacklist (compromised token detection)
4. Extract username from JWT
5. Validate against Redis storage
6. Atomic rotation - delete old token
7. Generate new token pair and store

**Logout Flow:**
1. Extract username for logging
2. Delete refresh token from Redis
3. Blacklist token to prevent reuse

### 5. API Endpoints

#### POST `/api/v1/auth/refresh`
Refresh access token using refresh token.

**Request:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "john.doe",
  "roles": ["ROLE_ADMIN", "ROLE_USER"],
  "expiresAt": "2026-05-19T15:30:00.000+00:00",
  "message": "Token refreshed successfully"
}
```

**Error Responses:**
- `401 UNAUTHORIZED` - Invalid/expired/blacklisted refresh token
- `400 BAD_REQUEST` - Validation errors

#### POST `/api/v1/auth/logout`
Logout with token revocation.

**Request:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response (200 OK):**
```
"Logout successful"
```

### 6. Authentication Service Integration

**Login Flow Enhancement:**
- Refresh tokens are now stored in Redis during initial login
- Ensures all refresh tokens are tracked from creation

**Methods Added:**
- `refreshAccessToken(RefreshTokenRequest)` - Token refresh
- `logout(String refreshToken)` - Token revocation

### 7. JWT Filter Enhancement

**Token Type Validation:**
- Access tokens must have `type: "access"` claim
- Refresh tokens rejected in authorization header
- Prevents misuse of refresh tokens for API access

---

## Security Flow Diagrams

### Token Refresh Flow
```
Client                          API Gateway                      Redis
  |                                 |                              |
  |-- POST /auth/refresh --------->|                              |
  |   {refreshToken}               |                              |
  |                                 |-- 1. Validate JWT --------->|
  |                                 |   (structure, type, exp)     |
  |                                 |                              |
  |                                 |-- 2. Check blacklist ------>|
  |                                 |                              |
  |                                 |-- 3. Get token data ------->|
  |                                 |<-- username, roles ---------|
  |                                 |                              |
  |                                 |-- 4. Delete old token ----->|
  |                                 |   (atomic rotation)          |
  |                                 |                              |
  |                                 |-- 5. Generate new tokens     |
  |                                 |   (access + refresh)         |
  |                                 |                              |
  |                                 |-- 6. Store new refresh ---->|
  |                                 |   token                      |
  |                                 |                              |
  |<-- 200 OK --------------------|                              |
  |   {accessToken, refreshToken}  |                              |
```

### Login Flow with Refresh Token Storage
```
Client                          API Gateway                      Redis
  |                                 |                              |
  |-- POST /auth/basic ----------->|                              |
  |   {username, password}         |                              |
  |                                 |-- Authenticate user          |
  |                                 |                              |
  |                                 |-- Generate access token      |
  |                                 |-- Generate refresh token     |
  |                                 |                              |
  |                                 |-- Store refresh token ----->|
  |                                 |   (username + roles + TTL)   |
  |                                 |                              |
  |<-- 200 OK --------------------|                              |
  |   {accessToken, refreshToken}  |                              |
```

### Logout Flow with Token Revocation
```
Client                          API Gateway                      Redis
  |                                 |                              |
  |-- POST /auth/logout ---------->|                              |
  |   {refreshToken}               |                              |
  |                                 |-- 1. Delete refresh token ->|
  |                                 |                              |
  |                                 |-- 2. Blacklist token ------>|
  |                                 |   (prevent reuse)            |
  |                                 |                              |
  |<-- 200 OK --------------------|                              |
  |   "Logout successful"          |                              |
```

---

## Banking Security Compliance

### Ant Group Security Standards Met

✅ **Token Rotation** - One-time use refresh tokens prevent replay attacks  
✅ **Atomic Operations** - Race condition prevention in concurrent requests  
✅ **Dual Validation** - JWT + Redis validation ensures token integrity  
✅ **Blacklist Support** - Immediate revocation for compromised tokens  
✅ **Audit Trail** - Complete logging with trace IDs for compliance  
✅ **Token Type Enforcement** - Prevents refresh token misuse  
✅ **TTL Management** - Automatic expiration aligned with token lifetime  
✅ **Error Handling** - Graceful degradation without exposing sensitive data  

### Additional Security Measures

1. **Concurrent Refresh Attack Prevention**
   - Only one refresh operation per token
   - Subsequent attempts fail with "token already used"

2. **Username Consistency Check**
   - Validates JWT username matches Redis stored username
   - Detects token tampering

3. **Role Preservation**
   - Roles stored with refresh token in Redis
   - Ensures role consistency across refresh cycles

4. **Trace ID Propagation**
   - Every operation has unique trace ID
   - Enables end-to-end request tracking
   - Supports forensic analysis

---

## Configuration

### application.properties
```properties
# JWT configuration
jwt.secret=${JWT_KEY}
jwt.expiration=3600000          # 1 hour (access token)
jwt.refresh-expiration=86400000 # 24 hours (refresh token)

# Redis configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=2000ms
spring.data.redis.lettuce.pool.max-active=8
spring.data.redis.lettuce.pool.max-idle=8
spring.data.redis.lettuce.pool.min-idle=0
```

### Security Configuration
All auth endpoints are whitelisted (no authentication required):
- `/api/v1/auth/basic` - Login
- `/api/v1/auth/refresh` - Token refresh
- `/api/v1/auth/logout` - Logout
- `/api/v1/auth/public-key` - RSA public key

---

## Error Handling

### Error Codes
```java
AUTH_003 - Invalid refresh token
AUTH_005 - Token refresh failed
```

### Error Response Format
```json
{
  "errorCode": "AUTH_003",
  "message": "Invalid refresh token",
  "httpStatus": "UNAUTHORIZED"
}
```

### Error Scenarios

| Scenario | Error Code | Description |
|----------|-----------|-------------|
| Expired refresh token | AUTH_003 | Token expiration check failed |
| Invalid token type | AUTH_003 | Token is not a refresh token |
| Blacklisted token | AUTH_003 | Token has been revoked |
| Token not in Redis | AUTH_003 | Possible replay attack |
| Username mismatch | AUTH_003 | JWT username ≠ Redis username |
| Redis storage failure | AUTH_005 | Failed to store new refresh token |

---

## Testing Guidelines

### Unit Tests Required

1. **Refresh Token Validation**
   - Valid token refresh
   - Expired token rejection
   - Wrong token type rejection
   - Blacklisted token rejection

2. **Token Rotation**
   - Successful rotation
   - Concurrent rotation prevention
   - Old token invalidation

3. **Logout Flow**
   - Successful logout
   - Token blacklisting verification
   - Subsequent refresh rejection

4. **Storage Service**
   - Token storage with TTL
   - Token retrieval
   - Token deletion
   - Blacklist operations

### Integration Tests

1. End-to-end refresh flow
2. Login → Refresh → Logout lifecycle
3. Concurrent refresh attempts
4. Redis failure scenarios

---

## Monitoring and Alerting

### Key Metrics to Monitor

1. **Token Refresh Rate**
   - Successful refreshes per minute
   - Failed refreshes per minute

2. **Security Events**
   - Blacklisted token usage attempts
   - Replay attack detections
   - Username mismatches

3. **Redis Performance**
   - Storage operation latency
   - Delete operation latency
   - Connection pool utilization

### Alert Triggers

- Blacklisted token usage (CRITICAL)
- Multiple failed refresh attempts from same user (HIGH)
- Redis connectivity issues (HIGH)
- Token storage failures (MEDIUM)

---

## Production Deployment Checklist

- [ ] Redis cluster configured with high availability
- [ ] JWT secret key properly secured (environment variable)
- [ ] Token expiration values tuned for business requirements
- [ ] Rate limiting enabled on `/auth/refresh` endpoint
- [ ] Monitoring dashboards configured
- [ ] Alert rules defined for security events
- [ ] Audit log aggregation enabled
- [ ] Load testing completed for concurrent refresh scenarios
- [ ] Rollback plan documented
- [ ] Security review completed

---

## Future Enhancements

1. **Device Fingerprinting**
   - Bind refresh tokens to specific devices
   - Detect token theft across devices

2. **Geographic Restrictions**
   - Token usage limited to specific regions
   - Anomaly detection for location changes

3. **Refresh Token Scoping**
   - Different refresh tokens for different permission levels
   - Granular token revocation

4. **Machine Learning Anomaly Detection**
   - Detect unusual refresh patterns
   - Automatic suspicious activity flagging

---

## References

- **OWASP JWT Security Cheat Sheet**: https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_Cheat_Sheet_for_Java.html
- **RFC 7519 - JSON Web Token**: https://tools.ietf.org/html/rfc7519
- **Spring Security WebFlux**: https://docs.spring.io/spring-security/reference/webflux.html
- **Ant Group Security Standards**: Internal banking security guidelines

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-05-19 | Initial implementation with banking-grade security |

---

**Implementation Status**: ✅ COMPLETE  
**Security Review**: ⏳ PENDING  
**Production Ready**: ⏳ PENDING TESTING
