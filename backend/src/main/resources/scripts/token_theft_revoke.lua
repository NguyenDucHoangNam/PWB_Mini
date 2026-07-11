-- Token theft revocation: delete all active token keys listed, then delete the
-- sessions zset, then mark each token as revoked with userId (TTL = revoked TTL).
-- KEYS[1..N] = session:refresh_token:{token} keys for each known active token
-- KEYS[N+1] = sessions zset (e.g. user:sessions:{userId})
-- ARGV[1] = userId
-- ARGV[2] = revoked TTL seconds
-- ARGV[3..M] = tokens that were in the zset (to populate revoked keys)
-- Returns: number of revoked keys created

local userId = ARGV[1]
local revokedTtl = tonumber(ARGV[2])

local tokensCount = #ARGV - 2
local deleted = 0
for i = 1, tokensCount do
    local token = ARGV[2 + i]
    local activeKey = 'session:refresh_token:' .. token
    redis.call('DEL', activeKey)
    redis.call('SET', 'session:refresh_token:revoked:' .. token, userId, 'EX', revokedTtl)
    deleted = deleted + 1
end

local zsetKey = KEYS[#KEYS]
redis.call('DEL', zsetKey)

return deleted
