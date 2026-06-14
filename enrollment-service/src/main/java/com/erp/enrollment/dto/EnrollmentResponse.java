package com.erp.enrollment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnrollmentResponse {

    private String message;
    private String status;
    private String error;
    private Integer retryAfter;

    private EnrollmentResponse() {}

    public static EnrollmentResponse accepted() {
        EnrollmentResponse r = new EnrollmentResponse();
        r.message = "Enrollment accepted";
        r.status = "PROCESSING";
        return r;
    }

    public static EnrollmentResponse error(String error) {
        EnrollmentResponse r = new EnrollmentResponse();
        r.error = error;
        return r;
    }

    public static EnrollmentResponse rateLimited(int retryAfter) {
        EnrollmentResponse r = new EnrollmentResponse();
        r.error = "Too many requests";
        r.retryAfter = retryAfter;
        return r;
    }

    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public String getError() { return error; }
    public Integer getRetryAfter() { return retryAfter; }
}
