package com.erp.worker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an enrollment event consumed from Kafka.
 */
public class EnrollmentEvent {

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("courseId")
    private String courseId;

    @JsonProperty("timestamp")
    private String timestamp;

    public EnrollmentEvent() {}

    public EnrollmentEvent(String userId, String courseId, String timestamp) {
        this.userId = userId;
        this.courseId = courseId;
        this.timestamp = timestamp;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "EnrollmentEvent{userId='" + userId + "', courseId='" + courseId + "'}";
    }
}
