package com.qrpublic.apartment.adapter.ratelimit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitPolicy {
    private int capacity;
    private int refillToken;
    private Duration refillPeriod;
    private String pathPattern;
    private int order;

    public static RateLimitPolicy defaultPolicy() {
        return RateLimitPolicy.builder()
                .capacity(100)
                .refillToken(100)
                .refillPeriod(Duration.ofMinutes(1))
                .pathPattern("/**")
                .order(Integer.MAX_VALUE)
                .build();
    }

    public long getRefillPeriodMs() {
        return refillPeriod.toMillis();
    }
}
