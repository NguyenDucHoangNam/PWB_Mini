

local current = redis.call('INCR', KEYS[1])

if tonumber(current) == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
else

    redis.call('EXPIRE', KEYS[1], ARGV[1])
end

return current