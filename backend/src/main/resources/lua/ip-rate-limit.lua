-- ip-rate-limit.lua
-- Atomic INCR + EXPIRE pattern for IpRateLimitFilter.
--
-- KEYS[1] = rate-limit key (e.g. "rate_limit:ip:1.2.3.4")
-- ARGV[1] = window TTL in seconds
--
-- Returns the current counter value after the increment.
--
-- The TTL is attached on first hit (when the counter was just created) so a
-- subsequent crash between INCR and EXPIRE cannot leave a counter without
-- expiry. The expiry is also re-applied on every call so that an unusually
-- long-lived key still rolls forward on the next window edge.

local current = redis.call('INCR', KEYS[1])

if tonumber(current) == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
else
    -- Refresh TTL on subsequent increments so a window slides forward cleanly.
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end

return current