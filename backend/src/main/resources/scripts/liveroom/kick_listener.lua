-- KEYS[1]: room:status:{roomCode}
-- KEYS[2]: room:members:{roomCode}
-- KEYS[3]: room:waiting:{roomCode}
-- KEYS[4]: room:waiting_metadata:{roomCode}
-- ARGV[1]: userId
-- Returns 1 on success, 0 if not a member
local removed = redis.call('hdel', KEYS[2], ARGV[1])
if removed == 0 then
    return 0
end

local current = redis.call('hget', KEYS[1], 'currentParticipants')
if current and tonumber(current) > 0 then
    redis.call('hincrby', KEYS[1], 'currentParticipants', -1)
end

redis.call('zrem', KEYS[3], ARGV[1])
redis.call('hdel', KEYS[4], ARGV[1])
return 1