package com.erp.enrollment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);
    private static final String ENROLLMENT_TOPIC = "enrollment_reserved";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> seatReservationScript;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EnrollmentService(StringRedisTemplate redisTemplate,
                             DefaultRedisScript<Long> seatReservationScript,
                             KafkaTemplate<String, String> kafkaTemplate,
                             ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.seatReservationScript = seatReservationScript;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Result of an enrollment attempt.
     */
    public enum EnrollResult {
        ACCEPTED,
        DUPLICATE_REQUEST,
        ALREADY_ENROLLED,
        COURSE_FULL
    }

    /**
     * Process an enrollment request through the hot path:
     * 1. Idempotency check (Redis SETNX)
     * 2. Seat reservation (Redis Lua script)
     * 3. Event publishing (Kafka)
     */
    public EnrollResult enroll(String userId, String courseId, String idempotencyKey) throws Exception {
        // Step 1: Idempotency check via SETNX
        String idempotencyRedisKey = "idempotency:" + idempotencyKey;
        Boolean wasSet = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyRedisKey, "PROCESSING", IDEMPOTENCY_TTL);

        if (wasSet == null || !wasSet) {
            log.info("Duplicate request detected: idempotencyKey={}", idempotencyKey);
            return EnrollResult.DUPLICATE_REQUEST;
        }

        // Step 2: Atomic seat reservation via Lua script
        String courseSeatsKey = "course:" + courseId + ":seats";
        String courseUsersKey = "course:" + courseId + ":users";

        Long luaResult = redisTemplate.execute(
                seatReservationScript,
                Arrays.asList(courseSeatsKey, courseUsersKey),
                userId
        );

        if (luaResult == null) {
            log.error("Lua script returned null for courseId={}, userId={}", courseId, userId);
            throw new RuntimeException("Redis Lua script execution failed");
        }

        if (luaResult == 1L) {
            return EnrollResult.ALREADY_ENROLLED;
        } else if (luaResult == 2L) {
            return EnrollResult.COURSE_FULL;
        }

        // Step 3: Publish to Kafka (async, buffered by linger.ms=5)
        Map<String, String> event = new HashMap<>();
        event.put("userId", userId);
        event.put("courseId", courseId);
        event.put("timestamp", Instant.now().toString());

        String eventJson = objectMapper.writeValueAsString(event);

        // Partition key = courseId ensures sequential processing per course
        kafkaTemplate.send(ENROLLMENT_TOPIC, courseId, eventJson);

        log.info("Enrollment accepted: userId={}, courseId={}", userId, courseId);
        return EnrollResult.ACCEPTED;
    }
}
