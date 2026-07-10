-- Atomically verify an OTP and clear all related state on success.
--
-- KEYS[1] = otp:registration:{email}
-- KEYS[2] = otp:cooldown:{email}
-- KEYS[3] = otp:attempts:{email}
-- ARGV[1] = submitted OTP
--
-- Returns:
--   0 = invalid OTP (no state change)
--   1 = valid OTP and state cleared

local stored = redis.call('GET', KEYS[1])
if stored == false or stored == nil then
    return 0
end
if stored == ARGV[1] then
    redis.call('DEL', KEYS[1])
    redis.call('DEL', KEYS[2])
    redis.call('DEL', KEYS[3])
    return 1
end
return 0