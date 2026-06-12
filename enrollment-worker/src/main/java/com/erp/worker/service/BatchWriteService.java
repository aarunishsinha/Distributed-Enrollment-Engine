package com.erp.worker.service;

import com.erp.worker.dlq.DlqProducer;
import com.erp.worker.model.EnrollmentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Performs batched UPSERT writes to PostgreSQL.
 * 
 * Strategy for partial batch failures:
 * 1. Try batch UPSERT (200 rows in one SQL statement)
 * 2. On failure: fall back to individual UPSERTs to isolate bad records
 * 3. DLQ only the failing records (not the entire batch)
 */
@Service
public class BatchWriteService {

    private static final Logger log = LoggerFactory.getLogger(BatchWriteService.class);

    private static final String UPSERT_SQL =
            "INSERT INTO enrollments (course_id, user_id) VALUES (?, ?) " +
            "ON CONFLICT (course_id, user_id) DO NOTHING";

    private final JdbcTemplate jdbcTemplate;
    private final DlqProducer dlqProducer;

    public BatchWriteService(JdbcTemplate jdbcTemplate, DlqProducer dlqProducer) {
        this.jdbcTemplate = jdbcTemplate;
        this.dlqProducer = dlqProducer;
    }

    /**
     * Write a batch of enrollment events to Postgres.
     * Returns the number of successfully written records.
     */
    public int writeBatch(List<EnrollmentEvent> events) {
        if (events.isEmpty()) return 0;

        try {
            // Attempt batch UPSERT — single SQL round-trip for all events
            int[] results = jdbcTemplate.batchUpdate(UPSERT_SQL, events, events.size(),
                    (PreparedStatement ps, EnrollmentEvent event) -> {
                        ps.setString(1, event.getCourseId());
                        ps.setString(2, event.getUserId());
                    });

            int successCount = 0;
            for (int result : results) {
                if (result >= 0 || result == PreparedStatement.SUCCESS_NO_INFO) {
                    successCount++;
                }
            }

            log.info("Batch write complete: {}/{} events written", successCount, events.size());
            return successCount;

        } catch (Exception batchError) {
            // Batch failed — fall back to individual writes to isolate bad records
            log.warn("Batch write failed, falling back to individual writes: {}",
                    batchError.getMessage());
            return writeIndividually(events, batchError);
        }
    }

    /**
     * Fallback: write events one by one, DLQ the failures.
     */
    private int writeIndividually(List<EnrollmentEvent> events, Exception originalError) {
        int successCount = 0;

        for (EnrollmentEvent event : events) {
            try {
                jdbcTemplate.update(UPSERT_SQL, event.getCourseId(), event.getUserId());
                successCount++;
            } catch (Exception individualError) {
                if (isTransient(individualError)) {
                    // Transient error on individual write — still DLQ it since batch already failed
                    log.warn("Transient error on individual write for {}: {}",
                            event, individualError.getMessage());
                    dlqProducer.sendToDlq(event, individualError, 1);
                } else {
                    // Non-transient error — FK violation, constraint error, etc.
                    log.error("Non-transient error for {}: {}", event, individualError.getMessage());
                    dlqProducer.sendToDlq(event, individualError, 0);
                }
            }
        }

        log.info("Individual fallback complete: {}/{} events written, {} sent to DLQ",
                successCount, events.size(), events.size() - successCount);
        return successCount;
    }

    private boolean isTransient(Exception error) {
        if (error instanceof SQLException sqlEx) {
            String sqlState = sqlEx.getSQLState();
            if (sqlState != null) {
                // 08* = connection exception, 53* = insufficient resources
                return sqlState.startsWith("08") || sqlState.startsWith("53");
            }
        }
        String msg = error.getMessage() != null ? error.getMessage().toLowerCase() : "";
        return msg.contains("connection") || msg.contains("timeout");
    }
}
