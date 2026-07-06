package com.pwb.backend.iam.internal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.List;

@Configuration
public class RedisLuaConfig {

  @Bean
  @SuppressWarnings("unchecked")
  public RedisScript<List<String>> concurrentSessionScript() {
    String script = "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])\n"
        + "local count = redis.call('ZCARD', KEYS[1])\n"
        + "local max_sessions = tonumber(ARGV[3])\n"
        + "local deleted_tokens = {}\n"
        + "if count > max_sessions then\n"
        + "    deleted_tokens = redis.call('ZRANGE', KEYS[1], 0, count - max_sessions - 1)\n"
        + "    redis.call('ZREMRANGEBYRANK', KEYS[1], 0, count - max_sessions - 1)\n"
        + "end\n"
        + "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4]))\n"
        + "return deleted_tokens";
    
    DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
    redisScript.setScriptText(script);
    redisScript.setResultType(List.class);
    return (RedisScript<List<String>>) (RedisScript<?>) redisScript;
  }

  @Bean
  public RedisScript<String> sessionRotationScript() {
    String script = "redis.call('SET', KEYS[2], ARGV[2], 'EX', tonumber(ARGV[4]))\n"
        + "redis.call('SET', KEYS[3], ARGV[1], 'EX', tonumber(ARGV[5]))\n"
        + "redis.call('DEL', KEYS[1])\n"
        + "redis.call('SET', KEYS[4], ARGV[1], 'EX', tonumber(ARGV[6]))\n"
        + "redis.call('ZREM', KEYS[5], ARGV[3])\n"
        + "redis.call('ZADD', KEYS[5], tonumber(ARGV[7]), ARGV[2])\n"
        + "redis.call('EXPIRE', KEYS[5], tonumber(ARGV[6]))\n"
        + "return 'SUCCESS'";

    DefaultRedisScript<String> redisScript = new DefaultRedisScript<>();
    redisScript.setScriptText(script);
    redisScript.setResultType(String.class);
    return redisScript;
  }

  @Bean
  @SuppressWarnings("unchecked")
  public RedisScript<List<String>> revokeOtherSessionsScript() {
    String script = "local current_token = ARGV[1]\n"
        + "local user_sessions_key = KEYS[1]\n"
        + "local tokens = redis.call('zrange', user_sessions_key, 0, -1)\n"
        + "local blacklisted_signatures = {}\n"
        + "for _, token in ipairs(tokens) do\n"
        + "    if token ~= current_token then\n"
        + "        local metadata_key = 'session:metadata:' .. token\n"
        + "        local refresh_key = 'session:refresh_token:' .. token\n"
        + "        local sig = redis.call('hget', metadata_key, 'active_jwt_signature')\n"
        + "        if sig then\n"
        + "            table.insert(blacklisted_signatures, sig)\n"
        + "        end\n"
        + "        redis.call('del', refresh_key)\n"
        + "        redis.call('del', metadata_key)\n"
        + "        redis.call('zrem', user_sessions_key, token)\n"
        + "    end\n"
        + "end\n"
        + "return blacklisted_signatures";

    DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
    redisScript.setScriptText(script);
    redisScript.setResultType(List.class);
    return (RedisScript<List<String>>) (RedisScript<?>) redisScript;
  }
}
