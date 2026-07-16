local key = KEYS[1]
local current = tonumber(redis.call("GET", key) or "0")
if current <= 0 then
  return 0
end
return redis.call("DECR", key)
