-- Atomically rotates a session's refresh + access tokens (anti-replay).
--
-- KEYS[1] = old refresh token key
-- KEYS[2] = new refresh token key
-- KEYS[3] = new access token metadata key
-- KEYS[4] = new session:user:{userId} entry key
-- KEYS[5] = user_sessions:{userId} (sorted set)
-- ARGV[1] = new access token metadata
-- ARGV[2] = new refresh token
-- ARGV[3] = old token (member to remove from sorted set)
-- ARGV[4] = new refresh ttl seconds
-- ARGV[5] = new access ttl seconds
-- ARGV[6] = user session ttl seconds
-- ARGV[7] = new token timestamp (score)
--
-- Returns: "SUCCESS"

redis.call('SET', KEYS[2], ARGV[2], 'EX', tonumber(ARGV[4]))
redis.call('SET', KEYS[3], ARGV[1], 'EX', tonumber(ARGV[5]))
redis.call('DEL', KEYS[1])
redis.call('SET', KEYS[4], ARGV[1], 'EX', tonumber(ARGV[6]))
redis.call('ZREM', KEYS[5], ARGV[3])
redis.call('ZADD', KEYS[5], tonumber(ARGV[7]), ARGV[2])
redis.call('EXPIRE', KEYS[5], tonumber(ARGV[6]))
return 'SUCCESS'
