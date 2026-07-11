-- Refresh Token Rotation (RTR).
-- Atomic renewal of refresh token + zset sync.
-- KEYS[1] = active token key for the OLD token, e.g. session:refresh_token:{oldToken}
-- KEYS[2] = active token key for the NEW token, e.g. session:refresh_token:{newToken}
-- KEYS[3] = shadow key for the OLD token,      e.g. session:refresh_token:shadow:{oldToken}
-- KEYS[4] = sessions zset,                     e.g. user:sessions:{userId}
-- ARGV[1] = old token (to ZREM from sessions zset)
-- ARGV[2] = new token (to ZADD into sessions zset)
-- ARGV[3] = userId   (must match existingUserId)
-- ARGV[4] = shadow TTL seconds
-- ARGV[5] = active TTL seconds
-- ARGV[6] = zset TTL seconds
-- ARGV[7] = now score for ZADD
-- Returns: {userId, status}
--   status = ROTATED   on success
--   status = NOT_FOUND if KEYS[1] missing or userId mismatch

local oldToken = ARGV[1]
local newToken = ARGV[2]
local userId = ARGV[3]
local shadowTtl = tonumber(ARGV[4])
local activeTtl = tonumber(ARGV[5])
local zsetTtl = tonumber(ARGV[6])
local now = ARGV[7]

local existingUserId = redis.call('GET', KEYS[1])
if not existingUserId or existingUserId ~= userId then
    return {'', 'NOT_FOUND'}
end

redis.call('SET', KEYS[2], userId, 'EX', activeTtl)
redis.call('SET', KEYS[3], newToken, 'EX', shadowTtl)
redis.call('ZREM', KEYS[4], oldToken)
redis.call('ZADD', KEYS[4], now, newToken)
redis.call('EXPIRE', KEYS[4], zsetTtl)
redis.call('DEL', KEYS[1])

return {userId, 'ROTATED'}
