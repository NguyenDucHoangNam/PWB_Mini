

redis.call('SET', KEYS[2], ARGV[2], 'EX', tonumber(ARGV[6]))
redis.call('DEL', KEYS[1])
redis.call('ZREM', KEYS[3], ARGV[3])
redis.call('ZADD', KEYS[3], tonumber(ARGV[7]), ARGV[2])
redis.call('EXPIRE', KEYS[3], tonumber(ARGV[6]))
return 'SUCCESS'