-- KEYS[1]: room:status:{roomCode}
-- Returns 1 if there is room and incremented, 0 otherwise
local current = redis.call('hget', KEYS[1], 'currentParticipants')
local max = redis.call('hget', KEYS[1], 'maxParticipants')

if not current or not max then
    return 0
end

if tonumber(current) < tonumber(max) then
    redis.call('hincrby', KEYS[1], 'currentParticipants', 1)
    return 1
else
    return 0
end