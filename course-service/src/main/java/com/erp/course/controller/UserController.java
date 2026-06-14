package com.erp.course.controller;

import com.erp.course.dto.UserDto;
import com.erp.course.model.Enrollment;
import com.erp.course.repository.EnrollmentRepository;
import com.erp.course.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class UserController {

    private final UserService userService;
    private final EnrollmentRepository enrollmentRepository;

    public UserController(UserService userService, EnrollmentRepository enrollmentRepository) {
        this.userService = userService;
        this.enrollmentRepository = enrollmentRepository;
    }

    @GetMapping("/v1/admin/users")
    public ResponseEntity<?> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Role", defaultValue = "STUDENT") String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(userService.listUsers(pageable));
    }

    @PostMapping("/v1/admin/users")
    public ResponseEntity<?> addUser(@RequestBody UserDto dto,
                                     @RequestHeader(value = "X-User-Role", defaultValue = "STUDENT") String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }
        try {
            UserDto created = userService.addUser(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/v1/admin/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable String userId,
                                        @RequestHeader(value = "X-User-Role", defaultValue = "STUDENT") String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }
        boolean deleted = userService.deleteUser(userId);
        if (deleted) return ResponseEntity.noContent().build();
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/v1/users/{userId}/enrollments")
    public ResponseEntity<List<Map<String, Object>>> getUserEnrollments(@PathVariable String userId) {
        List<Enrollment> enrollments = enrollmentRepository.findByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Enrollment e : enrollments) {
            Map<String, Object> map = new HashMap<>();
            map.put("enrollmentId", e.getEnrollmentId());
            map.put("courseId", e.getCourseId());
            map.put("status", e.getStatus());
            map.put("createdAt", e.getCreatedAt());
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }
}
