package com.qrpublic.apartment.adapter.ratelimit.constant;

public final class RateLimitHeaders {
    public static final String X_RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    public static final String X_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    public static final String X_RATE_LIMIT_RESET = "X-RateLimit-Reset";
    public static final String RETRY_AFTER = "Retry-After";


    public static final String AUTHORIZATION ="Authorization";
    public static final String X_USER_ID ="X-User-Id";
    private RateLimitHeaders() {
        // Private constructor to prevent instantiation
    }
}
