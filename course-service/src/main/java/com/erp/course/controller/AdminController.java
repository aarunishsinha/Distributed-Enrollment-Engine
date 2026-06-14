package com.erp.course.controller;

import com.erp.course.dto.AnalyticsSummary;
import com.erp.course.service.AnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final AnalyticsService analyticsService;

    public AdminController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/analytics")
    public ResponseEntity<?> getAnalytics(
            @RequestHeader(value = "X-User-Role", defaultValue = "STUDENT") String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }
        AnalyticsSummary summary = analyticsService.getAnalytics();
        return ResponseEntity.ok(summary);
    }
}
