# Global Exception Handling Architecture

## Overview

The application now implements centralized exception handling using Spring's `@RestControllerAdvice` pattern. This
ensures consistent error responses across all endpoints and reduces boilerplate error handling code in controllers.

## Architecture

### Core Components

#### 1. Exception Hierarchy

```
Exception (Java)
└── RuntimeException
    └── ApplicationException (Base for all app-specific exceptions)
        ├── EnvironmentCreationException
        ├── ResourceNotFoundException
        └── ValidationException
```

### 2. GlobalExceptionHandler

**Location:** `com.qrpublic.apartment.exception.handler.GlobalExceptionHandler`

A centralized exception handler that intercepts and processes all exceptions thrown by the application.

**Key Features:**

- Catches all application-specific exceptions
- Handles validation errors (from @Valid)
- Handles type mismatches
- Provides fallback for unexpected exceptions
- Returns consistent `Result<T>` response format

### 3. Error Response Format

All error responses follow a consistent structure using the `Result<T>` template model:

```json
{
  "success": false,
  "data": null,
  "errorMessage": "Failed to create sale environment. Please try again later.",
  "errorCode": 500
}
```

### 4. Supported Exception Handlers

| Exception                             | HTTP Status        | Handler Method                         |
|---------------------------------------|--------------------|----------------------------------------|
| `ApplicationException`                | Based on errorCode | `handleApplicationException()`         |
| `EnvironmentCreationException`        | 500                | `handleEnvironmentCreationException()` |
| `ResourceNotFoundException`           | 404                | `handleResourceNotFoundException()`    |
| `ValidationException`                 | 400                | `handleValidationException()`          |
| `MethodArgumentNotValidException`     | 400                | `handleMethodArgumentNotValid()`       |
| `MethodArgumentTypeMismatchException` | 400                | `handleMethodArgumentTypeMismatch()`   |
| Generic `Exception`                   | 500                | `handleGeneralException()`             |

## Usage in Controllers

### Before (With Manual Error Handling)

```java

@PostMapping("/createSaleEnvironment")
public Mono<ResponseEntity<CreateEnvironmentResponse>> generatePublicLink(@Valid @RequestBody CreateEnvironmentRequest request) {
    return adminService.createSaleEnvironment(request)
            .map(response -> {
                log.info("Sale environment created successfully");
                return response;
            })
            .onErrorResume(ApplicationException.class, error -> {
                // Manual error handling code...
                Result<CreateEnvironmentResponse> errorResult = ErrorHandler.buildErrorResult(error);
                return Mono.just(ResponseEntity.status(status).body(errorResult));
            });
}
```

### After (With GlobalExceptionHandler)

```java

@PostMapping("/createSaleEnvironment")
public Mono<ResponseEntity<CreateEnvironmentResponse>> generatePublicLink(@Valid @RequestBody CreateEnvironmentRequest request) {
    log.info("Received request to create sale environment");
    return adminService.createSaleEnvironment(request)
            .map(response -> {
                log.info("Sale environment created successfully");
                return response;
            });
}
```

**Benefits:**

- Controllers focus on business logic
- Exception handling is centralized
- Consistent error responses across all endpoints
- Easier to maintain and modify error handling globally
- Reduced code duplication

## Exception Throwing Pattern

### In Service Layer

```java

@Service
public class AdminService {
    public Mono<ResponseEntity<CreateEnvironmentResponse>> createSaleEnvironment(CreateEnvironmentRequest request) {
        return Mono.just(response)
                .map(ResponseEntity::ok)
                .onErrorMap(throwable ->
                        new EnvironmentCreationException("Failed to create sale environment. Please try again later."));
    }
}
```

### Using onErrorMap for Reactive Streams

When working with Project Reactor (Mono/Flux):

- Use `onErrorMap()` to transform errors into application exceptions
- The transformed exception propagates to `GlobalExceptionHandler`
- Avoid `onErrorComplete()` which swallows exceptions

## Example Responses

### Success Response

```json
{
  "success": true,
  "data": {
    "publicLink": "https://example.com/secure/abc123",
    "requestId": "req-12345",
    "createdAt": "2026-04-22T10:30:00"
  },
  "errorMessage": null,
  "errorCode": 200
}
```

### Environment Creation Failure

```json
{
  "success": false,
  "data": null,
  "errorMessage": "Failed to create sale environment. Please try again later.",
  "errorCode": 500
}
```

### Validation Error

```json
{
  "success": false,
  "data": null,
  "errorMessage": "email: must be a valid email address, name: must not be blank",
  "errorCode": 400
}
```

### Resource Not Found

```json
{
  "success": false,
  "data": null,
  "errorMessage": "Resource not found",
  "errorCode": 404
}
```

## Adding New Exception Types

### Step 1: Create Custom Exception

```java
package com.qrpublic.apartment.exception;

import org.springframework.http.HttpStatus;

public class CustomException extends ApplicationException {

    public CustomException(String message) {
        super(message, HttpStatus.BAD_REQUEST.value());
    }

    public CustomException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_REQUEST.value());
    }
}
```

### Step 2: Add Exception Handler (Optional)

If you need specific handling logic:

```java

@ExceptionHandler(CustomException.class)
public ResponseEntity<Result<?>> handleCustomException(CustomException ex) {
    log.warn("Custom error: {}", ex.getErrorMessage());
    Result<?> errorResult = ErrorHandler.buildErrorResult(ex);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResult);
}
```

If no specific handler is defined, the `handleApplicationException()` method will handle it automatically.

## Logging Strategy

All exception handlers log appropriate messages:

- **ERROR Level:** Unexpected general exceptions
- **WARN Level:** Application-specific exceptions (expected errors)

This allows ops/monitoring teams to focus on ERROR level logs for system issues.

## Testing Exception Handling

### Test Example

```java

@Test
void testEnvironmentCreationFailure() {
    CreateEnvironmentRequest request = new CreateEnvironmentRequest();

    when(saleEnvironmentService.createSaleEnvironment(any()))
            .thenThrow(new EnvironmentCreationException("Test error"));

    ResponseEntity<Result<?>> response =
            globalExceptionHandler.handleEnvironmentCreationException(
                    new EnvironmentCreationException("Test error")
            );

    assertFalse(response.getBody().isSuccess());
    assertEquals(500, response.getStatusCode().value());
}
```

## Migration Checklist

When integrating this pattern into existing endpoints:

- [ ] Remove manual error handling from controller methods
- [ ] Ensure service methods throw appropriate application exceptions
- [ ] Use `onErrorMap()` in reactive streams to throw exceptions
- [ ] Remove redundant try-catch blocks
- [ ] Test error scenarios to ensure GlobalExceptionHandler catches them
- [ ] Update API documentation with consistent error response format

## Related Files

- `GlobalExceptionHandler.java` - Centralized exception handling
- `ApplicationException.java` - Base exception class
- `EnvironmentCreationException.java` - Environment creation errors
- `ResourceNotFoundException.java` - Resource not found errors
- `ValidationException.java` - Validation errors
- `ErrorHandler.java` - Utility for building error responses
- `Result<T>.java` - Response template model

