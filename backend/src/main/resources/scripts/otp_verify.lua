-- Atomic OTP verification.
-- KEYS[1] = otp key        (e.g. otp:user@example.com)
-- KEYS[2] = attempt key    (e.g. otp:attempt:user@example.com)
-- KEYS[3] = lockout key    (e.g. otp:lock:user@example.com)
-- ARGV[1] = submitted OTP
-- ARGV[2] = lockout TTL seconds
-- ARGV[3] = attempt TTL seconds
-- ARGV[4] = max attempts
-- Returns: {result, code}
--   result: 1 = success, 0 = failure
--   code:   OK, LOCKED, NOT_FOUND, WRONG

if redis.call('GET', KEYS[3]) then
    return {0, 'LOCKED'}
end

local stored = redis.call('GET', KEYS[1])
if not stored then
    return {0, 'NOT_FOUND'}
end

if stored == ARGV[1] then
    redis.call('DEL', KEYS[1])
    redis.call('DEL', KEYS[2])
    return {1, 'OK'}
end

local attempts = redis.call('INCR', KEYS[2])
if attempts >= tonumber(ARGV[4]) then
    redis.call('DEL', KEYS[1])
    redis.call('DEL', KEYS[2])
    redis.call('SET', KEYS[3], '1', 'EX', ARGV[2])
    return {0, 'LOCKED'}
end

if attempts == 1 then
    redis.call('EXPIRE', KEYS[2], ARGV[3])
end

return {0, 'WRONG'}