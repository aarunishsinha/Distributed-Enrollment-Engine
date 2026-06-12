package com.erp.enrollment.exception;

import com.erp.enrollment.dto.EnrollmentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<EnrollmentResponse> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.badRequest()
                .body(EnrollmentResponse.error("Missing required header: " + e.getHeaderName()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<EnrollmentResponse> handleGeneral(Exception e) {
        log.error("Unhandled exception: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(EnrollmentResponse.error("Internal Server Error"));
    }
}
