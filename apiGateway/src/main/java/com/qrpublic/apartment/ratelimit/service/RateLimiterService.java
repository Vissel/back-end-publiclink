package com.qrpublic.apartment.ratelimit.service;

import com.qrpublic.apartment.adapter.ratelimit.model.RateLimitPolicy;
import com.qrpublic.apartment.adapter.ratelimit.model.RateLimitResult;
import reactor.core.publisher.Mono;

public interface RateLimiterService {

    Mono<RateLimitResult> isAllowed(String key, RateLimitPolicy policy);
}
