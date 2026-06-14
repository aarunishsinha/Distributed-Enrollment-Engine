package com.erp.worker.consumer;

import com.erp.worker.model.EnrollmentEvent;
import com.erp.worker.service.BatchWriteService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch Kafka consumer for enrollment events.
 * 
 * Consumes up to 200 messages per poll (max.poll.records=200),
 * writes them as a single batch UPSERT to Postgres,
 * then manually acknowledges the offset.
 */
@Component
public class EnrollmentBatchConsumer {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentBatchConsumer.class);

    private final BatchWriteService batchWriteService;
    private final ObjectMapper objectMapper;

    public EnrollmentBatchConsumer(BatchWriteService batchWriteService, ObjectMapper objectMapper) {
        this.batchWriteService = batchWriteService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "enrollment_reserved", groupId = "enrollment-worker-group")
    public void onBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        log.info("Received batch of {} messages", records.size());

        List<EnrollmentEvent> events = new ArrayList<>(records.size());

        for (ConsumerRecord<String, String> record : records) {
            try {
                EnrollmentEvent event = objectMapper.readValue(record.value(), EnrollmentEvent.class);
                events.add(event);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize message at offset {}: {}",
                        record.offset(), e.getMessage());
                // Skip malformed messages — they'll never be parseable, don't retry
            }
        }

        if (!events.isEmpty()) {
            int written = batchWriteService.writeBatch(events);
            log.info("Batch processing complete: {}/{} events persisted", written, events.size());
        }

        // Acknowledge the entire batch — even if some went to DLQ,
        // they've been handled (routed to DLQ for admin review)
        ack.acknowledge();
    }
}
