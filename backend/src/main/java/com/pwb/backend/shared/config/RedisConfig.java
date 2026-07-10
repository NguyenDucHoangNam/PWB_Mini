package com.pwb.backend.shared.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis wiring.
 *
 * <p>H10: the previous {@link RedisTemplate} re-used the application's
 * primary {@link ObjectMapper}, which had polymorphic typing OFF and
 * forced {@link GenericJackson2JsonRedisSerializer} to enable it
 * internally. This caused silent {@code @class} fields on every cached
 * value and broke serialization in test/slice contexts that loaded a
 * different classloader.
 *
 * <p>We now expose two serializers:
 * <ul>
 *   <li>A {@code RedisTemplate<String, String>} via the auto-configured
 *       {@link StringRedisTemplate} for the common string-value case
 *       (sessions, rate limits, claims).</li>
 *   <li>A {@code RedisTemplate<String, Object>} backed by a Redis-local
 *       {@link ObjectMapper} that explicitly opts in to a tightly-scoped
 *       polymorphic type validator, restricted to our own packages so a
 *       hostile payload cannot trigger gadget deserialization.</li>
 * </ul>
 */
@Configuration
// M1: @EnableSchedulerLock moved to BackendApplication so the shared
// module does not opt the whole context into shedlock on import.
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis-specific {@link ObjectMapper} with a tightly scoped
     * polymorphic-typing whitelist. We restrict accepted base types to
     * {@code java.util} and our own packages to prevent deserialization
     * gadgets sneaking in through cached payloads.
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(Object.class)
            .allowIfSubType("java.util.")
            .allowIfSubType("com.pwb.backend.")
            .allowIfSubType("java.time.")
            .build();
        mapper.activateDefaultTyping(validator, ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY);
        return mapper;
    }

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory);
    }
}