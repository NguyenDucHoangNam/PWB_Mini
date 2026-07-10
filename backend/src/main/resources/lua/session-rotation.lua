-- Atomically rotates a session's refresh + access tokens (anti-replay).
--
-- KEYS[1] = old refresh token key  (session:refresh_token:{oldToken})
-- KEYS[2] = new refresh token key  (session:refresh_token:{newToken})
-- KEYS[3] = user_sessions:{userId} (sorted set)
-- ARGV[1] = new access token signature
-- ARGV[2] = new refresh token (member to add to sorted set)
-- ARGV[3] = old token (member to remove from sorted set)
-- ARGV[4] = refresh token grace window seconds (small, for shadow-key TTL)
-- ARGV[5] = new access token TTL seconds
-- ARGV[6] = new refresh token TTL seconds (real expiry, e.g. 7 days)
-- ARGV[7] = new token timestamp (score for sorted set)
--
-- Returns: "SUCCESS"
--
-- NOTE: The Java side (SessionMetadataBuilder.store) writes the session
-- metadata as a Redis Hash after this script returns. Do NOT add a
-- SET for the metadata key here — that would overwrite the Hash with a
-- plain String and break getActiveSessions / revokeSession.

redis.call('SET', KEYS[2], ARGV[2], 'EX', tonumber(ARGV[6]))
redis.call('DEL', KEYS[1])
redis.call('ZREM', KEYS[3], ARGV[3])
redis.call('ZADD', KEYS[3], tonumber(ARGV[7]), ARGV[2])
redis.call('EXPIRE', KEYS[3], tonumber(ARGV[6]))
return 'SUCCESS'