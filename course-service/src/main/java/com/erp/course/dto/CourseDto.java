package com.erp.course.dto;

import com.erp.course.model.Course;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CourseDto {

    private String courseId;
    private String courseName;
    private String department;
    private String description;
    private int totalCapacity;
    private int availableSeats;
    private boolean isActive;
    private LocalDateTime createdAt;
    private Long enrollmentCount;

    public CourseDto() {}

    public static CourseDto fromEntity(Course course) {
        CourseDto dto = new CourseDto();
        dto.courseId = course.getCourseId();
        dto.courseName = course.getCourseName();
        dto.department = course.getDepartment();
        dto.description = course.getDescription();
        dto.totalCapacity = course.getTotalCapacity();
        dto.availableSeats = course.getAvailableSeats();
        dto.isActive = course.isActive();
        dto.createdAt = course.getCreatedAt();
        return dto;
    }

    public static CourseDto fromEntityWithEnrollments(Course course, long enrollmentCount) {
        CourseDto dto = fromEntity(course);
        dto.enrollmentCount = enrollmentCount;
        return dto;
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
    public Long getEnrollmentCount() { return enrollmentCount; }
}
