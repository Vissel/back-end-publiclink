package com.qrpublic.apartment.ratelimit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private String redisKeyPrefix = "rl:";
    private FallbackStrategy fallbackStrategy = FallbackStrategy.ALLOW;
    /**
     * default policy configuration for the token bucket algorithm. You can customize this for different routes if needed.
     */
    private PolicyConfig policy = new PolicyConfig();

    /**
     * Path-specific rate limit policies (first match wins). You can define different rate limit configurations for different API paths.
     * Order is determined by the 'order' field in PolicyConfig, lower values are applied first. If multiple policies match the same path, the one with the lowest order will be applied.
     */
    private List<PolicyConfig> policies = new ArrayList<>();

    private List<String> excludePaths = new ArrayList<>(); // Paths to exclude from rate limiting

    /**
     * Trusted proxy IP ranges for correct client IP extraction
     * X-Forwarded-For header is used to determine the original client IP when requests pass through proxies. To prevent IP spoofing, only consider IPs from trusted proxies.
     */
    private List<String> trustedProxyCidrs = new ArrayList<>();

    public enum FallbackStrategy {
        /**
         * Allow all requests when Redis is down (fail-open)
         */
        ALLOW,
        /**
         * Deny all requests when Redis is down (fail-closed)
         */
        DENY,
        /**
         * Fall back to local in-memory rate limiting when Redis is unavailable
         */
        DEGRADED;
    }

    @Data
    public static class PolicyConfig {
        private String pathPattern = "/**"; // Ant-style path pattern to match requests
        private int capacity = 100; // Maximum number of requests in the bucket
        private int refillTokens = 10; // Number of tokens to add per refill interval
        private int refillPeriodSeconds = 60; // Refill interval in seconds
        private int order = 0; // Filter order, lower values are applied first

        public Duration getRefillPeriod() {
            return Duration.ofSeconds(refillPeriodSeconds);
        }
    }
}
