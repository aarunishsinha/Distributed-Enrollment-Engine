package com.erp.course.service;

import com.erp.course.dto.AnalyticsSummary;
import com.erp.course.repository.CourseRepository;
import com.erp.course.repository.EnrollmentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AnalyticsService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    public AnalyticsService(CourseRepository courseRepository, EnrollmentRepository enrollmentRepository) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    public AnalyticsSummary getAnalytics() {
        AnalyticsSummary summary = new AnalyticsSummary();

        summary.setTotalCourses(courseRepository.count());
        summary.setActiveCourses(courseRepository.countByIsActiveTrue());
        summary.setTotalEnrollments(enrollmentRepository.count());

        // Calculate total capacity from all active courses
        long totalCapacity = courseRepository.findAll().stream()
                .filter(c -> c.isActive())
                .mapToLong(c -> c.getTotalCapacity())
                .sum();
        summary.setTotalCapacity(totalCapacity);

        if (totalCapacity > 0) {
            summary.setUtilizationPercent(
                    Math.round(((double) summary.getTotalEnrollments() / totalCapacity) * 1000.0) / 10.0);
        }

        // Top courses by enrollment
        List<Object[]> topCourses = enrollmentRepository.topCoursesByEnrollment();
        List<Map<String, Object>> topList = new ArrayList<>();
        for (Object[] row : topCourses) {
            Map<String, Object> map = new HashMap<>();
            map.put("courseId", row[0]);
            map.put("enrollments", row[1]);
            topList.add(map);
        }
        summary.setTopCourses(topList);

        // Department breakdown
        List<Object[]> depts = enrollmentRepository.enrollmentsByDepartment();
        List<Map<String, Object>> deptList = new ArrayList<>();
        for (Object[] row : depts) {
            Map<String, Object> map = new HashMap<>();
            map.put("department", row[0] != null ? row[0] : "Unspecified");
            map.put("enrollments", row[1]);
            deptList.add(map);
        }
        summary.setDepartmentBreakdown(deptList);

        // Time-series (last 30 days)
        List<Object[]> ts = enrollmentRepository.enrollmentTimeseries(LocalDateTime.now().minusDays(30));
        List<Map<String, Object>> tsList = new ArrayList<>();
        for (Object[] row : ts) {
            Map<String, Object> map = new HashMap<>();
            map.put("date", row[0].toString());
            map.put("enrollments", row[1]);
            tsList.add(map);
        }
        summary.setTimeseries(tsList);

        return summary;
    }
}
