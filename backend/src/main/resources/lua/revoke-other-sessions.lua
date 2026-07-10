

if current_token == nil or current_token == '' then
    return redis.error_reply('current_token is required to identify session to keep')
end

local current_token = ARGV[1]
local blacklist_prefix = ARGV[2]
local blacklist_ttl = tonumber(ARGV[3])
local user_sessions_key = KEYS[1]

local tokens = redis.call('zrange', user_sessions_key, 0, -1)
local evicted = {}
for _, token in ipairs(tokens) do
    if token ~= current_token then
        local metadata_key = 'session:metadata:' .. token
        local refresh_key = 'session:refresh_token:' .. token
        local sig = redis.call('hget', metadata_key, 'active_jwt_signature')
        if sig and sig ~= '' then
            redis.call('set', blacklist_prefix .. sig, 'true', 'EX', blacklist_ttl)
        end
        redis.call('del', refresh_key)
        redis.call('del', metadata_key)
        redis.call('zrem', user_sessions_key, token)
        table.insert(evicted, token)
    end
end
return evicted