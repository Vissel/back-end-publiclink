# develop-feature

## Project Context
- **Architecture**: Microservice system with Spring WebFlux (reactive), Java backend, ReactJS + MUI frontend (web + mobile)
- **Backend Stack**: Spring Boot 3, Spring WebFlux (Reactor), Spring Data JPA, JWT authentication
- **Gateway**: Spring Cloud Gateway with JWT validation and claim extraction
- **Modules**: `apiGateway`, `adapter`, `authen-authorisation`, `publiclink-app`, `user`

---

## Critical Rules (Always Follow)

### 1. NEVER Use `.block()` in WebFlux Reactive Threads
**Problem**: Calling `.block()`, `.blockFirst()`, `.blockLast()` in reactive chains causes:
```
IllegalStateException: block()/blockFirst()/blockLast() are blocking, which is not supported in thread reactor-http-nio-X
```

**Rule**: In any method called from a WebFlux controller or service, use reactive operators exclusively:
- `flatMap()` — chain async operations that return Mono/Flux
- `map()` — transform values synchronously
- `onErrorResume()` — graceful error recovery per-item
- `Mono.fromCallable()` — wrap blocking/sync code for async execution
- `collectMap()` — collect Flux results into a Map

**Anti-pattern**:
```java
// WRONG - blocks the reactive thread
FindUserAuthenResponse authResponse = client.findUserByUsername(request).block();
dto.setAuthLink(authResponse.getToken());
return dto;
```

**Correct pattern**:
```java
// CORRECT - fully reactive
return client.findUserByUsername(request)
    .map(authResponse -> {
        dto.setAuthLink(authResponse.getToken());
        return dto;
    })
    .onErrorResume(e -> {
        log.warn("Failed to fetch auth data: {}", e.getMessage());
        return Mono.just(dto);
    });
```

**Method signature change**: When adding reactive calls, change return type from `T` to `Mono<T>` and update caller chain accordingly.

### 2. JPQL Nested Field Access Requires Explicit JOIN
**Problem**: JPQL cannot navigate nested fields like `r.createdBy.username` without explicit JOIN.

**Anti-pattern**:
```java
@Query("SELECT e FROM SaleEnvironment e WHERE e.request.createdBy.username = :username")
// ERROR: Cannot resolve symbol 'createdBy.username'
```

**Correct pattern**:
```java
@Query("SELECT e FROM SaleEnvironment e LEFT JOIN e.request r LEFT JOIN r.createdBy cb WHERE cb.userName = :username")
```

### 3. Spring Data JPA PageRequest Uses Zero-Based Index
**Problem**: Frontend sends 1-based page numbers, but `PageRequest.of()` expects 0-based.

**Rule**: Always subtract 1 from frontend page number:
```java
PageRequest pageable = PageRequest.of(pageFromFrontend - 1, size, Sort.by(Sort.Order.desc("createdAt")));
```

### 4. Centralize Shared Data Extraction (No Duplication)
**Problem**: Duplicating data extraction logic (e.g., `sellerName = dto != null ? dto.getName() : null`) across reactive branches leads to missed updates.

**Rule**: Extract shared data once at the top of the method, then reuse in all branches.

**Example**:
```java
private SellerModel createOrGetSeller(RequestDTO requestDTO) {
    String username = requestDTO.getUsername();
    SellerDTO sellerDTO = requestDTO.getSeller();
    String sellerName = sellerDTO != null ? sellerDTO.getName() : null; // centralized once

    return client.findUserByUsername(...)
        .flatMap(findUser -> {
            // use sellerName here
            return generateToken(username, sellerName, ...);
        })
        .switchIfEmpty(Mono.defer(() -> {
            // use sellerName here too
            return generateToken(username, sellerName, ...);
        }));
}
```

### 5. Idempotent Entity Updates with "If Missing" Pattern
When updating an entity field that might already have a value, make it idempotent:
```java
@Transactional
public void updateSellerNameIfMissing(String requestUuid, String sellerName) {
    if (sellerName == null || sellerName.isBlank()) return;
    requestRepository.findByReqUUID(requestUuid).ifPresent(request -> {
        if (request.getSellerName() == null || request.getSellerName().isBlank()) {
            request.setSellerName(sellerName);
            requestRepository.save(request);
        }
    });
}
```

### 6. Reactive Batch Lookup with Graceful Per-Item Error Handling
**Pattern**: When fetching multiple items from external services, failures on individual items should not break the entire flow.

```java
Flux.fromIterable(usernames)
    .flatMap(username -> client.findUserByUsername(new FindUserAuthenRequest(username))
        .map(this::convertToModel)
        .timeout(Duration.ofSeconds(3))
        .onErrorResume(error -> {
            log.error("Failed to fetch seller [{}]: {}", username, error.getMessage());
            return Mono.empty(); // skip failed item, continue with others
        }))
    .collectMap(SellerModel::getUsername)
```

### 7. Mono.empty vs. Empty-Field Object Distinction
**Rule**: `Mono.empty()` means "no data at all" (triggers `switchIfEmpty`). If data exists but a field is null, return `Mono.just(object)` with the null field — don't use `Mono.empty()`.

```java
// WRONG - causes switchIfEmpty to trigger even though user exists
if (response.getUserName() == null) return Mono.empty();

// CORRECT - user exists, just has no name
return Mono.just(new SellerModel(response.getUserName(), null));
```

### 8. JWT Claim Propagation Flow
When adding a new JWT claim (e.g., `name`), update the entire chain:
1. Request DTO (e.g., `PubUserRequest`, `UserAuthenTokenRequest`)
2. Service layer (extract and pass the field)
3. Gateway controller (receive and forward)
4. Token producer (embed as JWT claim)
5. Consumer services (extract from token as needed)

### 9. reqUuid End-to-End Propagation
- Propagate `reqUuid` through all request/response DTOs in the authentication flow
- Only include in success responses (not on error)
- Use it to lookup related entities (e.g., `SaleEnvironment` by `request.reqUUID`)

### 10. WebFlux Controller Return Types
Controllers must return reactive types when calling reactive services:
```java
// WRONG - blocks in controller
public ResponseEntity<SaleEnvDTO> getDetail(String uuid) {
    SaleEnvDTO dto = service.getDetail(uuid); // blocks!
    return ResponseEntity.ok(dto);
}

// CORRECT - fully reactive
public Mono<ResponseEntity<SaleEnvDTO>> getDetail(String uuid) {
    return service.getDetail(uuid)
        .map(ResponseEntity::ok)
        .defaultIfEmpty(ResponseEntity.notFound().build());
}
```

---

## JPA / Entity Mapping Rules

### @OneToMany Requires @JoinColumn
```java
@OneToMany(mappedBy = "request", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
@JoinColumn(name = "req_id")  // prevents optimistic locking errors
private List<Pricing> pricings;
```

### Required Imports for Spring Data JPA Custom Queries
```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```

---

## Reactive Method Transformation Checklist
When converting a blocking method to reactive:
1. [ ] Change return type from `T` to `Mono<T>` or `Flux<T>`
2. [ ] Replace `.block()` calls with `.map()`, `.flatMap()`, `.switchIfEmpty()`
3. [ ] Wrap sync/blocking code in `Mono.fromCallable()`
4. [ ] Add `.onErrorResume()` for graceful degradation
5. [ ] Update all callers to handle reactive return types
6. [ ] Use `.defaultIfEmpty()` or `.switchIfEmpty()` for missing data scenarios
7. [ ] Preserve timeout configuration on external calls

---

## Microservice Communication Patterns
- **Synchronous (reactive)**: Use `WebClient` returning `Mono<T>` / `Flux<T>` — never block
- **External service calls**: Always wrap in `.timeout()` and `.onErrorResume()`
- **Compensating transactions**: On failure after partial success, rollback what was committed
- **Batch operations**: Use `Flux.fromIterable().flatMap()` with per-item error handling

---

## Common Error Messages and Fixes

| Error | Cause | Fix |
|-------|-------|-----|
| `block() are blocking, which is not supported` | Using `.block()` in reactive thread | Replace with `.map()` / `.flatMap()` chain |
| `Cannot resolve symbol 'x.y'` in JPQL | Missing JOIN for nested field | Add `LEFT JOIN x y` and reference via alias |
| `No static resource` on valid endpoint | Path mismatch or wrong HTTP method | Verify `@GetMapping/@PostMapping` and path |
| `OptimisticLockException` on `@OneToMany` | Missing `@JoinColumn` on collection | Add `@JoinColumn(name = "...")` |
| Wrong page data (stale rows) | Not converting 1-based to 0-based | `PageRequest.of(page - 1, size)` |
| `switchIfEmpty` triggers unexpectedly | Returning `Mono.empty()` instead of object with null field | Return `Mono.just(object)` even with null fields |
