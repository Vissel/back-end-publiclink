package com.qrpublic.apartment.adapter.ratelimit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResult {
    private boolean allowed;
    private long remainingTokens;
    private long limit;
    private long resetAtEpochSeconds;
    private long retryAfterSeconds;

    public static RateLimitResult allowed(long remainingTokens, long limit, long resetAtEpochSeconds) {
        return RateLimitResult.builder()
                .allowed(true)
                .remainingTokens(remainingTokens)
                .limit(limit)
                .resetAtEpochSeconds(resetAtEpochSeconds)
                .retryAfterSeconds(0)
                .build();
    }

    public static RateLimitResult denied(long remainning, long limit, long resetAtEpochSecond, long retryAfterSeconds) {
        return RateLimitResult.builder()
                .allowed(false)
                .remainingTokens(remainning)
                .limit(limit)
                .resetAtEpochSeconds(resetAtEpochSecond)
                .retryAfterSeconds(retryAfterSeconds)
                .build();
    }
}
