package com.erp.course.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "enrollment_id")
    private Long enrollmentId;

    @Column(name = "course_id", length = 50)
    private String courseId;

    @Column(name = "user_id", length = 50)
    private String userId;

    @Column(name = "status", length = 20)
    private String status = "CONFIRMED";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Getters
    public Long getEnrollmentId() { return enrollmentId; }
    public String getCourseId() { return courseId; }
    public String getUserId() { return userId; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
