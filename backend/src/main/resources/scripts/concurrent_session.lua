-- Atomic concurrent session control for the user.
-- KEYS[1] = sessions zset (e.g. user:sessions:{userId})
-- ARGV[1] = max concurrent sessions (e.g. 3)
-- ARGV[2] = new refresh token (UUID string)
-- ARGV[3] = score = epoch seconds
-- ARGV[4] = zset TTL seconds (e.g. 604800)
-- Returns: kickedToken (oldest token string) or empty string if none

local maxSessions = tonumber(ARGV[1])
local newToken = ARGV[2]
local now = ARGV[3]
local ttl = tonumber(ARGV[4])

local size = redis.call('ZCARD', KEYS[1])

local kicked = ''
if size >= maxSessions then
    local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
    if oldest and #oldest >= 1 then
        kicked = oldest[1]
        redis.call('ZREM', KEYS[1], kicked)
    end
end

redis.call('ZADD', KEYS[1], now, newToken)
redis.call('EXPIRE', KEYS[1], ttl)

return kicked
