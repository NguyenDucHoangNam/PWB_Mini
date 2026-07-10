-- "Sign out other devices" — revokes every session except the current one.
--
-- KEYS[1] = user_sessions:{userId} (sorted set)
-- ARGV[1] = current token (kept, others are revoked)
--
-- Returns: list of JWT signatures to blacklist for the evicted tokens.

local current_token = ARGV[1]
local user_sessions_key = KEYS[1]
local tokens = redis.call('zrange', user_sessions_key, 0, -1)
local blacklisted_signatures = {}
for _, token in ipairs(tokens) do
    if token ~= current_token then
        local metadata_key = 'session:metadata:' .. token
        local refresh_key = 'session:refresh_token:' .. token
        local sig = redis.call('hget', metadata_key, 'active_jwt_signature')
        if sig then
            table.insert(blacklisted_signatures, sig)
        end
        redis.call('del', refresh_key)
        redis.call('del', metadata_key)
        redis.call('zrem', user_sessions_key, token)
    end
end
return blacklisted_signatures
