package com.erp.worker.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.worker.model.EnrollmentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes failed enrollment events to the Dead Letter Queue
 * with enriched error metadata for admin investigation.
 */
@Component
public class DlqProducer {

    private static final Logger log = LoggerFactory.getLogger(DlqProducer.class);
    private static final String DLQ_TOPIC = "enrollment_dlq";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DlqProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Send a failed event to the DLQ with enriched metadata.
     */
    public void sendToDlq(EnrollmentEvent event, Exception error, int retryCount) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();

            Map<String, String> originalPayload = new HashMap<>();
            originalPayload.put("userId", event.getUserId());
            originalPayload.put("courseId", event.getCourseId());
            originalPayload.put("timestamp", event.getTimestamp());
            dlqMessage.put("originalPayload", originalPayload);

            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("message", error.getMessage());
            errorInfo.put("type", isTransient(error) ? "TRANSIENT" : "NON_TRANSIENT");
            if (error.getCause() != null) {
                errorInfo.put("cause", error.getCause().getMessage());
            }
            dlqMessage.put("error", errorInfo);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("retryCount", retryCount);
            metadata.put("failedAt", Instant.now().toString());
            dlqMessage.put("metadata", metadata);

            String json = objectMapper.writeValueAsString(dlqMessage);
            kafkaTemplate.send(DLQ_TOPIC, event.getCourseId(), json);
            log.warn("Sent to DLQ: {}", event);

        } catch (Exception dlqError) {
            // If we can't even write to DLQ, log the error with full context
            log.error("CRITICAL: Failed to send to DLQ. Original event: {}, Error: {}",
                    event, error.getMessage(), dlqError);
        }
    }

    private boolean isTransient(Exception error) {
        String msg = error.getMessage() != null ? error.getMessage().toLowerCase() : "";
        return msg.contains("connection") || msg.contains("timeout") || msg.contains("refused");
    }
}
