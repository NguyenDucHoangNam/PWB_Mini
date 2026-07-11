-- Login brute-force attempt counter & lockout
-- KEYS[1] = attempts key   (e.g. login_attempts:{userId})
-- KEYS[2] = lockout key    (e.g. login_lockout:{userId})
-- ARGV[1] = max attempts (e.g. 5)
-- ARGV[2] = attempt TTL seconds (e.g. 900)
-- ARGV[3] = lockout TTL seconds (e.g. 900)
-- Returns: {newCount, locked}
--   newCount: total failed attempts in current window
--   locked:   1 if lockout was just triggered, 0 otherwise

local maxAttempts = tonumber(ARGV[1])
local attemptTtl = tonumber(ARGV[2])
local lockoutTtl = tonumber(ARGV[3])

if redis.call('EXISTS', KEYS[2]) == 1 then
    return {0, 0}
end

local attempts = redis.call('INCR', KEYS[1])
if attempts == 1 then
    redis.call('EXPIRE', KEYS[1], attemptTtl)
end

if attempts >= maxAttempts then
    redis.call('SET', KEYS[2], '1', 'EX', lockoutTtl)
    redis.call('DEL', KEYS[1])
    return {attempts, 1}
end

return {attempts, 0}
