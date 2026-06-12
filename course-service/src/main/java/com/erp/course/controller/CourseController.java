package com.erp.course.controller;

import com.erp.course.dto.CourseDto;
import com.erp.course.service.CourseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public ResponseEntity<Page<CourseDto>> listCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(courseService.listActiveCourses(pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<CourseDto>> searchCourses(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(courseService.searchCourses(query.trim(), pageable));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDto> getCourse(@PathVariable String courseId) {
        return courseService.getCourseById(courseId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> addCourse(@RequestBody CourseDto dto,
                                       @RequestHeader(value = "X-User-Role", defaultValue = "STUDENT") String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }

        try {
            CourseDto created = courseService.addCourse(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<?> deleteCourse(@PathVariable String courseId,
                                          @RequestHeader(value = "X-User-Role", defaultValue = "STUDENT") String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }

        boolean deleted = courseService.deleteCourse(courseId);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
