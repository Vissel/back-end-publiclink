package com.qrpublic.apartment.apiGateway.ratelimit.service;

import com.qrpublic.apartment.adapter.ratelimit.model.RateLimitPolicy;
import com.qrpublic.apartment.adapter.ratelimit.model.RateLimitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
public class RedisTokenRateLimiterImpl implements RateLimiterService {
    @Autowired
    ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    @Autowired
    RedisScript<List<Long>> tokenBucketLuaScript;

    @Override
    public Mono<RateLimitResult> isAllowed(String key, RateLimitPolicy policy) {
        long nowMs = System.currentTimeMillis();
        List<String> keys = List.of(key);
        List<String> args = List.of(
                String.valueOf(policy.getCapacity()),
                String.valueOf(policy.getRefillToken()),
                String.valueOf(policy.getRefillPeriodMs()),
                String.valueOf(nowMs),
                "1"); // 1 token to consume
        return reactiveRedisTemplate.execute(tokenBucketLuaScript, keys, args)
                .next()
                .map(result -> parserResult(result, policy));
    }

    RateLimitResult parserResult(List<Long> result, RateLimitPolicy policy) {
        boolean allowed = result.getFirst() == 1L;
        long remainingTokens = result.get(1);
        long resetAtEpochMs = result.get(2);
        long retryAfterMs = result.get(3);

        long resetEpochSeconds = resetAtEpochMs / 1000;
        long retryAfterSeconds = (long) Math.ceil(retryAfterMs / 1000);

        if (allowed) {
            return RateLimitResult.allowed(remainingTokens, policy.getCapacity(), resetEpochSeconds);
        } else {
            return RateLimitResult.denied(remainingTokens, policy.getCapacity(), resetEpochSeconds, retryAfterSeconds);
        }
    }
}
