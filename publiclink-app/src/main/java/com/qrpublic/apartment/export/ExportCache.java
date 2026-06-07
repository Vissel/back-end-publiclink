package com.qrpublic.apartment.export;

import com.qrpublic.apartment.saleenv.request.ExportAllRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for streaming export requests.
 * 
 * The two-step streaming export pattern works as follows:
 * 1. Frontend POSTs export parameters → backend stores in cache with UUID token
 * → returns token
 * 2. Frontend GETs /stream/{token} → backend retrieves and removes from cache →
 * streams file
 * 
 * This pattern avoids:
 * - Sending large request bodies in GET requests
 * - Buffering entire file in memory before sending
 * - Token reuse (removed after first retrieval)
 * 
 * Thread-safe via ConcurrentHashMap — multiple concurrent exports supported.
 */
@Component
public class ExportCache {

    private final ConcurrentHashMap<String, ExportAllRequest> cache = new ConcurrentHashMap<>();

    /**
     * Store an export request and return a unique token.
     * Token is single-use — removed when retrieved via {@link #consume(String)}.
     */
    public String store(ExportAllRequest request) {
        String token = UUID.randomUUID().toString();
        cache.put(token, request);
        return token;
    }

    /**
     * Retrieve and remove the export request for the given token.
     * Returns null if token is invalid or already consumed.
     */
    public ExportAllRequest consume(String token) {
        return cache.remove(token);
    }

}
