-- Refresh Token Rotation (RTR) + Shadow grace resolution, performed atomically.
-- KEYS[1] = active token key for the OLD token (session:refresh_token:{oldToken})
-- KEYS[2] = shadow key for the OLD token        (session:refresh_token:shadow:{oldToken})
-- KEYS[3] = revoked key for the OLD token       (session:refresh_token:revoked:{oldToken})
-- KEYS[4] = sessions zset                       (user:sessions:{userId})
-- ARGV[1] = old token (used to ZREM from sessions zset)
-- ARGV[2] = expected userId from JWT
-- ARGV[3] = shadow TTL seconds
-- ARGV[4] = zset TTL seconds
-- Returns: {status, action, newToken, newActiveKey}
--   status:  OK | NOT_FOUND | SHADOW_HIT | REVOKED
--   action:  ROTATED  | RETURN_SHADOW  |  REVOKE_ALL
--   newToken / newActiveKey populated only on SHADOW_HIT

local oldToken = ARGV[1]
local userId = ARGV[2]
local shadowTtl = tonumber(ARGV[3])
local zsetTtl = tonumber(ARGV[4])

local existingUserId = redis.call('GET', KEYS[1])
if existingUserId then
    if existingUserId == userId then
        return {'OK', 'ROTATE', '', ''}
    end
    return {'NOT_FOUND', '', '', ''}
end

local revokedUserId = redis.call('GET', KEYS[3])
if revokedUserId then
    if revokedUserId == userId then
        return {'REVOKED', 'REVOKE_ALL', '', ''}
    end
    return {'NOT_FOUND', '', '', ''}
end

local shadowNewToken = redis.call('GET', KEYS[2])
if shadowNewToken and shadowNewToken ~= '' then
    local newActiveKey = 'session:refresh_token:' .. shadowNewToken
    local shadowOwner = redis.call('GET', newActiveKey)
    if shadowOwner and shadowOwner == userId then
        return {'SHADOW_HIT', 'RETURN_SHADOW', shadowNewToken, newActiveKey}
    end
end

return {'NOT_FOUND', '', '', ''}
