-- KEYS[1]: room:status:{roomCode}
-- KEYS[2]: room:members:{roomCode}
-- KEYS[3]: room:waiting:{roomCode}
-- KEYS[4]: room:waiting_metadata:{roomCode}
-- ARGV[1]: userId
-- Returns 1 if anything was removed, 0 otherwise
local touched = 0

local removedMember = redis.call('hdel', KEYS[2], ARGV[1])
if removedMember == 1 then
    touched = 1
    local current = redis.call('hget', KEYS[1], 'currentParticipants')
    if current and tonumber(current) > 0 then
        redis.call('hincrby', KEYS[1], 'currentParticipants', -1)
    end
end

local removedWaiting = redis.call('zrem', KEYS[3], ARGV[1])
if removedWaiting == 1 then
    touched = 1
end

local removedMeta = redis.call('hdel', KEYS[4], ARGV[1])
if removedMeta == 1 then
    touched = 1
end

return touched