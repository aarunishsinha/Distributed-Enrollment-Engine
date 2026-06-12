package com.erp.course.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @Column(name = "course_id", length = 50)
    private String courseId;

    @Column(name = "course_name", nullable = false, length = 255)
    private String courseName;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and setters
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(int totalCapacity) { this.totalCapacity = totalCapacity; }

    public int getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(int availableSeats) { this.availableSeats = availableSeats; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
