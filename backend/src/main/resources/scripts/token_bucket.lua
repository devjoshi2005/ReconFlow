-- token_bucket.lua
-- KEYS[1] = "rate:upload:<userId>"
-- ARGV[1] = capacity (max burst, e.g. 10)
-- ARGV[2] = refill_rate (tokens per second, e.g. 0.5 = 1 token every 2s)
-- ARGV[3] = cost (tokens to consume, usually 1)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local cost = tonumber(ARGV[3]) or 1

-- Use Redis server clock (avoids app-server clock skew)
local t = redis.call('TIME')
local now = tonumber(t[1]) + tonumber(t[2]) / 1000000.0

-- Load state
local data = redis.call('HMGET', key, 'tokens', 'last')
local tokens = data[1] and tonumber(data[1]) or capacity
local last = data[2] and tonumber(data[2]) or now

-- Continuous refill: tokens += elapsed * rate, capped at capacity
local elapsed = math.max(0, now - last)
tokens = math.min(capacity, tokens + elapsed * rate)

local allowed = 0
local remaining = tokens
local retry_after = 0

if tokens >= cost then
    allowed = 1
    tokens = tokens - cost
    remaining = tokens
else
    -- How long until enough tokens accumulate
    retry_after = math.ceil((cost - tokens) / rate)
end

-- Persist
redis.call('HSET', key, 'tokens', tostring(tokens), 'last', tostring(now))
-- Auto-expire idle keys: time to full refill + 60s buffer
redis.call('EXPIRE', key, math.ceil(capacity / rate) + 60)

return {allowed, math.floor(remaining), retry_after}
