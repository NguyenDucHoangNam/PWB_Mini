-- Captcha failure counter: atomically increment the failure window
-- and return the new total.
--
-- KEYS[1] = failure counter key
-- ARGV[1] = tracking window seconds (e.g. 900)
-- Returns: new failure count after increment

local windowSec = tonumber(ARGV[1])
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], windowSec)
end
return count
