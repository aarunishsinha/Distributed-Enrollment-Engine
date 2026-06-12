package com.erp.enrollment.controller;

import com.erp.enrollment.dto.EnrollmentRequest;
import com.erp.enrollment.dto.EnrollmentResponse;
import com.erp.enrollment.service.EnrollmentService;
import com.erp.enrollment.service.EnrollmentService.EnrollResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/enrollments")
public class EnrollmentController {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentController.class);

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    public ResponseEntity<EnrollmentResponse> enroll(
            @RequestBody EnrollmentRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        if (request.getCourseId() == null || request.getCourseId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(EnrollmentResponse.error("courseId is required"));
        }

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(EnrollmentResponse.error("X-User-Id header is required"));
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(EnrollmentResponse.error("Idempotency-Key header is required"));
        }

        try {
            EnrollResult result = enrollmentService.enroll(userId, request.getCourseId(), idempotencyKey);

            return switch (result) {
                case ACCEPTED -> ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(EnrollmentResponse.accepted());
                case DUPLICATE_REQUEST -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(EnrollmentResponse.error("Conflict: Request already processed or in progress"));
                case ALREADY_ENROLLED -> ResponseEntity.badRequest()
                        .body(EnrollmentResponse.error("User is already enrolled in this course"));
                case COURSE_FULL -> ResponseEntity.badRequest()
                        .body(EnrollmentResponse.error("Course is full"));
            };
        } catch (Exception e) {
            log.error("Enrollment error for userId={}, courseId={}: {}",
                    userId, request.getCourseId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(EnrollmentResponse.error("Internal Server Error"));
        }
    }
}
