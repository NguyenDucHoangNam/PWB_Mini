-- Captcha challenge tracker: counts failed attempts and decides whether
-- a captcha challenge must be shown to the current client.
--
-- KEYS[1] = failure counter key
--           (e.g. captcha_fail:{scope}:{ip}:{identifier})
-- ARGV[1] = failure threshold (e.g. 3)
-- ARGV[2] = tracking window seconds (e.g. 900)
-- Returns: {currentCount, requiresChallenge}
--   currentCount:      total failures recorded so far in the window
--   requiresChallenge: 1 if currentCount >= threshold, 0 otherwise

local threshold = tonumber(ARGV[1])
local windowSec = tonumber(ARGV[2])

local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local requires = 0
if current >= threshold then
    requires = 1
end

return {current, requires}
