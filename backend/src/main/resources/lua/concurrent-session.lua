

redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
local count = redis.call('ZCARD', KEYS[1])
local max_sessions = tonumber(ARGV[3])
local deleted_tokens = {}
if count > max_sessions then
    deleted_tokens = redis.call('ZRANGE', KEYS[1], 0, count - max_sessions - 1)
    redis.call('ZREMRANGEBYRANK', KEYS[1], 0, count - max_sessions - 1)
end
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4]))
return deleted_tokens
