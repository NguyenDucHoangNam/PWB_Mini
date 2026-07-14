-- Atomic resend-cooldown acquisition.
-- Returns 1 if the slot was acquired (cooldown started), 0 if cooldown still active.
-- KEYS[1] = otp:last-sent:{email}
-- ARGV[1] = cooldown seconds

if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end

redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
return 1