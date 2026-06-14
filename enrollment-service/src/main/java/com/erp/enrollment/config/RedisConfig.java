package com.erp.enrollment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Lua script for atomic seat reservation.
     * Ported verbatim from the original TypeScript implementation.
     *
     * KEYS[1] = course:{courseId}:seats   (available seat counter)
     * KEYS[2] = course:{courseId}:users   (set of enrolled user IDs)
     * ARGV[1] = userId
     *
     * Returns:
     *   0 = Success (seat reserved)
     *   1 = User already enrolled
     *   2 = Course full
     */
    @Bean
    public DefaultRedisScript<Long> seatReservationScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("scripts/seat-reservation.lua")));
        script.setResultType(Long.class);
        return script;
    }
}
