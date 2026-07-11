-- Grant the first refresh token for a fresh login.
-- KEYS[1] = new active token key, e.g. session:refresh_token:{newToken}
-- KEYS[2] = sessions zset,         e.g. user:sessions:{userId}
-- ARGV[1] = userId
-- ARGV[2] = new refresh token (UUID string)
-- ARGV[3] = now score (epoch seconds)
-- ARGV[4] = active TTL seconds
-- ARGV[5] = max concurrent sessions (e.g. 3)
-- Returns: kickedToken (oldest) or ''

local userId = ARGV[1]
local newToken = ARGV[2]
local now = ARGV[3]
local activeTtl = tonumber(ARGV[4])
local maxSessions = tonumber(ARGV[5])

redis.call('SET', KEYS[1], userId, 'EX', activeTtl)

local size = redis.call('ZCARD', KEYS[2])
local kicked = ''
if size >= maxSessions then
    local oldest = redis.call('ZRANGE', KEYS[2], 0, 0, 'WITHSCORES')
    if oldest and #oldest >= 1 then
        kicked = oldest[1]
        redis.call('ZREM', KEYS[2], kicked)
    end
end

redis.call('ZADD', KEYS[2], now, newToken)
redis.call('EXPIRE', KEYS[2], activeTtl)

return kicked
