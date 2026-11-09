# Global Exception Handling - Quick Reference

## What Changed?

The application now uses **centralized exception handling** via `@RestControllerAdvice`. No more manual error handling in controllers!

## Key Files

| File | Purpose |
|------|---------|
| `GlobalExceptionHandler.java` | Centralized exception handler (NEW) |
| `GenerationController.java` | Simplified controller (UPDATED) |
| `AdminService.java` | Uses onErrorMap for exceptions (UPDATED) |

## Exception Hierarchy

```
ApplicationException (base)
├── EnvironmentCreationException (500 - Internal Server Error)
├── ResourceNotFoundException (404 - Not Found)
└── ValidationException (400 - Bad Request)
```

## Response Format

### Success
```json
{
  "success": true,
  "data": { /* response data */ },
  "errorCode": 200
}
```

### Error
```json
{
  "success": false,
  "data": null,
  "errorMessage": "Error description",
  "errorCode": 500
}
```

## How to Throw Exceptions

### In Services (Synchronous)
```java
throw new EnvironmentCreationException("Failed to create sale environment");
```

### In Services (Reactive/Mono)
```java
return Mono.just(data)
    .map(ResponseEntity::ok)
    .onErrorMap(error -> new EnvironmentCreationException("Error message"));
```

### In Controllers
**NO NEED for try-catch or onErrorResume!** Just return the Mono, GlobalExceptionHandler will handle it.

```java
@PostMapping("/endpoint")
public Mono<ResponseEntity<Response>> endpoint(@RequestBody Request request) {
    return service.doSomething(request);
}
```

## How GlobalExceptionHandler Works

1. Exception thrown in service/controller
2. Spring catches the exception before response
3. `GlobalExceptionHandler` matches the exception type
4. Appropriate handler method is called
5. Consistent error response is returned to client
6. No boilerplate error handling needed in controllers!

## Adding New Exception Handling

### Option 1: Use Existing Exception (Recommended)
```java
throw new ApplicationException("Message", HttpStatus.BAD_REQUEST.value());
```

### Option 2: Create Custom Exception
```java
public class MyCustomException extends ApplicationException {
    public MyCustomException(String message) {
        super(message, HttpStatus.BAD_REQUEST.value());
    }
}
```

It will be handled automatically by `handleApplicationException()` in GlobalExceptionHandler.

### Option 3: Add Specific Handler
```java
@ExceptionHandler(MyCustomException.class)
public ResponseEntity<Result<?>> handleMyCustomException(MyCustomException ex) {
    log.warn("My custom error: {}", ex.getErrorMessage());
    Result<?> errorResult = ErrorHandler.buildErrorResult(ex);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResult);
}
```

## Common Scenarios

### Scenario 1: Validation Error
```java
if (!isValid(request)) {
    throw new ValidationException("Invalid request data");
}
```
→ Returns 400 with error message

### Scenario 2: Resource Not Found
```java
User user = userRepo.findById(id)
    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
```
→ Returns 404 with error message

### Scenario 3: Operation Failed
```java
return Mono.just(data)
    .onErrorMap(e -> new EnvironmentCreationException("Failed to create environment"))
```
→ Returns 500 with error message

### Scenario 4: Unexpected Error
Any unhandled exception automatically caught and returns:
```json
{
  "success": false,
  "errorMessage": "An unexpected error occurred. Please try again later.",
  "errorCode": 500
}
```

## Testing

### Verify GlobalExceptionHandler is Active
1. Throw an exception in any endpoint
2. Should receive consistent JSON error response
3. Should NOT receive server error page

### Example Test
```java
@Test
void testExceptionHandling() {
    // Trigger exception
    assertThrows(EnvironmentCreationException.class, 
        () -> service.createEnvironment(request));
}
```

## Benefits

✅ **Cleaner Code** - Controllers focus on business logic only  
✅ **Consistent Responses** - All errors follow same format  
✅ **Easier Maintenance** - Change error handling in one place  
✅ **Better Logging** - All exceptions logged centrally  
✅ **Less Duplication** - Remove repeated error handling code  
✅ **Standard Pattern** - Industry best practice  

## Reference Documentation

See `GLOBAL_EXCEPTION_HANDLING_GUIDE.md` for:
- Detailed architecture explanation
- Example error responses
- Migration checklist
- Advanced customization

