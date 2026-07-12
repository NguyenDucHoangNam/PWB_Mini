-- KEYS[1]: room:status:{roomCode}
-- KEYS[2]: room:waiting:{roomCode}
-- KEYS[3]: room:waiting_metadata:{roomCode}
-- KEYS[4]: room:members:{roomCode}
-- ARGV[1]: userId
-- ARGV[2]: memberMetadataJson
-- Returns 1 on success, 0 if room is full or status key missing
local current = redis.call('hget', KEYS[1], 'currentParticipants')
local max = redis.call('hget', KEYS[1], 'maxParticipants')

if not current or not max then
    return 0
end

if tonumber(current) < tonumber(max) then
    redis.call('hincrby', KEYS[1], 'currentParticipants', 1)
    redis.call('zrem', KEYS[2], ARGV[1])
    redis.call('hdel', KEYS[3], ARGV[1])
    redis.call('hset', KEYS[4], ARGV[1], ARGV[2])
    return 1
else
    return 0
end