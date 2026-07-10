

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