package com.erp.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Token Bucket rate limiter implemented as a Spring Cloud Gateway filter.
 * Uses a Redis Lua script for atomic token check-and-decrement.
 */
@Component
public class TokenBucketRateLimiter extends AbstractGatewayFilterFactory<TokenBucketRateLimiter.Config> {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${app.rate-limit.rate:5}")
    private int defaultRate;

    @Value("${app.rate-limit.burst:10}")
    private int defaultBurst;

    // Token Bucket Lua script (atomic check + decrement)
    private static final String TOKEN_BUCKET_LUA = """
        local key = KEYS[1]
        local rate = tonumber(ARGV[1])
        local burst = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])
        local requested = tonumber(ARGV[4])
        
        local data = redis.call("HMGET", key, "tokens", "last_refill")
        local tokens = tonumber(data[1])
        local last_refill = tonumber(data[2])
        
        if tokens == nil then
            tokens = burst
            last_refill = now
        end
        
        local elapsed = (now - last_refill) / 1000000
        local new_tokens = math.min(burst, tokens + (elapsed * rate))
        
        if new_tokens < requested then
            redis.call("HMSET", key, "tokens", new_tokens, "last_refill", now)
            redis.call("EXPIRE", key, 60)
            return 0
        end
        
        new_tokens = new_tokens - requested
        redis.call("HMSET", key, "tokens", new_tokens, "last_refill", now)
        redis.call("EXPIRE", key, 60)
        return 1
        """;

    private final DefaultRedisScript<Long> rateLimitScript;

    public TokenBucketRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;

        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setScriptText(TOKEN_BUCKET_LUA);
        this.rateLimitScript.setResultType(Long.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String userId = request.getHeaders().getFirst("X-User-Id");

            if (userId == null || userId.isBlank()) {
                // No user ID = can't rate limit, let it through (will fail at service level)
                return chain.filter(exchange);
            }

            String redisKey = "rate_limit:" + userId;
            long nowMicros = System.currentTimeMillis() * 1000; // microseconds

            return redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(redisKey),
                    java.util.List.of(
                            String.valueOf(defaultRate),
                            String.valueOf(defaultBurst),
                            String.valueOf(nowMicros),
                            "1"  // 1 token per request
                    )
            ).next().defaultIfEmpty(1L).flatMap(result -> {
                if (result == 0L) {
                    // Rate limited
                    log.info("Rate limited user: {}", userId);
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders().set("Retry-After", "2");
                    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

                    String body = "{\"error\":\"Too many requests\",\"retryAfter\":2}";
                    DataBuffer buffer = exchange.getResponse().bufferFactory()
                            .wrap(body.getBytes(StandardCharsets.UTF_8));
                    return exchange.getResponse().writeWith(Mono.just(buffer));
                }

                // Allowed — proceed to downstream service
                return chain.filter(exchange);
            });
        };
    }

    public static class Config {
        // Configuration properties if needed
    }
}
