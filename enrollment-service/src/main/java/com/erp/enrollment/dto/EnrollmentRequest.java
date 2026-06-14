package com.erp.enrollment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EnrollmentRequest {

    @JsonProperty("courseId")
    private String courseId;

    public EnrollmentRequest() {}

    public EnrollmentRequest(String courseId) {
        this.courseId = courseId;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }
}
