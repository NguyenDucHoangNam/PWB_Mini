local key = KEYS[1]
local limit = tonumber(ARGV[1])
local ttl_seconds = tonumber(ARGV[2])
local current = tonumber(redis.call("GET", key) or "0")
if current >= limit then
  return -1
end
local new_value = redis.call("INCR", key)
if new_value == 1 then
  redis.call("EXPIRE", key, ttl_seconds)
end
return new_value
