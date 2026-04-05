package com.qrpublic.apartment.ratelimit.resolver;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface KeyResolver {
    /**
     * Resolve a key for the given exchange. This key will be used for rate limiting decisions.
     *
     * @param exchange
     * @return
     */
    Mono<String> resolve(ServerWebExchange exchange);
}
