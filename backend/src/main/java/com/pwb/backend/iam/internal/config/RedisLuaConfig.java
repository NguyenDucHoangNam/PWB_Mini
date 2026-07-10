package com.pwb.backend.iam.internal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class RedisLuaConfig {

  @Bean
  public RedisScript<List<String>> concurrentSessionScript() {
    return loadList("lua/concurrent-session.lua");
  }

  @Bean
  public RedisScript<String> sessionRotationScript() {
    DefaultRedisScript<String> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("lua/session-rotation.lua"));
    script.setResultType(String.class);
    return script;
  }

  @Bean
  public RedisScript<List<String>> revokeOtherSessionsScript() {
    return loadList("lua/revoke-other-sessions.lua");
  }

  @Bean
  public RedisScript<Long> otpVerifyScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("lua/otp-verify.lua"));
    script.setResultType(Long.class);
    return script;
  }

  private RedisScript<List<String>> loadList(String classpathLocation) {
    DefaultRedisScript<List> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource(classpathLocation));
    script.setResultType(List.class);
    @SuppressWarnings("unchecked")
    RedisScript<List<String>> typed = (RedisScript<List<String>>) (RedisScript<?>) script;
    return typed;
  }
}
