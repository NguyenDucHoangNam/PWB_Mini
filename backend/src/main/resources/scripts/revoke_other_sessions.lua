-- Atomic revoke of all other sessions except the current one.
-- KEYS[1] = sessions zset, e.g. user:sessions:{userId}
-- ARGV[1] = current refresh token (UUID) that must be kept
-- Returns: { sig1, sig2, ... } list of active_jwt_signature values revoked

local zsetKey = KEYS[1]
local currentToken = ARGV[1]

local tokens = redis.call('ZRANGE', zsetKey, 0, -1)
local revokedSignatures = {}

for _, token in ipairs(tokens) do
    if token ~= currentToken then
        local metaKey = 'session:metadata:' .. token
        local refreshKey = 'session:refresh_token:' .. token
        local sig = redis.call('HGET', metaKey, 'active_jwt_signature')
        if sig then
            table.insert(revokedSignatures, sig)
        end
        redis.call('DEL', refreshKey)
        redis.call('DEL', metaKey)
        redis.call('ZREM', zsetKey, token)
    end
end

return revokedSignatures
