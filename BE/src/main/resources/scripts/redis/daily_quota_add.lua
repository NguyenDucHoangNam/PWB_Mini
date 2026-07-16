local key = KEYS[1]
local now = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local cutoff = now - window_ms

redis.call("ZREMRANGEBYSCORE", key, "-inf", cutoff)
local current = redis.call("ZCARD", key)
if current >= limit then
  return -1
end
redis.call("ZADD", key, now, now .. ":" .. math.random(1000000))
redis.call("PEXPIRE", key, window_ms)
return current + 1
