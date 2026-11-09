-- Token Bucket Rate Limiter Lua Script
-- KEYS[1]: rate limit key
-- ARGV[1]: bucket capacity
-- ARGV[2]: refill tokens per period
-- ARGV[3]: refill period in milliseconds
-- ARGV[4]: current time in milliseconds
-- ARGV[5]: tokens to consume

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillTokens = tonumber(ARGV[2])
local refillPeriodMs = tonumber(ARGV[3])
local nowMs = tonumber(ARGV[4])
local tokensToConsume = tonumber(ARGV[5])

-- Get current bucket state from Redis
local bucketData = redis.call('HMGET', key, 'tokens', 'lastRefillMs')
local tokens = tonumber(bucketData[1]) or capacity
local lastRefillMs = tonumber(bucketData[2]) or nowMs

-- Calculate elapsed time since last refill
local elapsedMs = math.max(0, nowMs - lastRefillMs)

-- Calculate how many tokens to add based on elapsed time
local periodsElapsed = elapsedMs / refillPeriodMs
local tokensToAdd = math.floor(periodsElapsed * refillTokens)

-- Update tokens, capped at capacity
tokens = math.min(capacity, tokens + tokensToAdd)

-- Calculate new refill time
local newLastRefillMs = lastRefillMs + (math.floor(periodsElapsed) * refillPeriodMs)

-- Check if request is allowed
local allowed = 0
if tokens >= tokensToConsume then
    allowed = 1
    tokens = tokens - tokensToConsume
end

-- Store updated bucket state
redis.call('HSET', key, 'tokens', tokens, 'lastRefillMs', newLastRefillMs)

-- Set expiration time (key expires 1 hour after last update)
redis.call('EXPIRE', key, 3600)

-- Calculate reset time (when bucket will be full again)
local tokensNeeded = capacity - tokens
local refillsNeeded = math.ceil(tokensNeeded / refillTokens)
local resetAtEpochMs = newLastRefillMs + (refillsNeeded * refillPeriodMs)

-- Calculate retry-after time (how long until 1 token is available)
local retryAfterMs = 0
if not (allowed == 1) then
    retryAfterMs = math.ceil((1 - tokens) / refillTokens * refillPeriodMs)
end

-- Return: {allowed, remainingTokens, resetAtEpochMs, retryAfterMs}
return {allowed, tokens, resetAtEpochMs, retryAfterMs}

