package com.erp.course.service;

import com.erp.course.dto.CourseDto;
import com.erp.course.model.Course;
import com.erp.course.repository.CourseRepository;
import com.erp.course.repository.EnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StringRedisTemplate redisTemplate;

    public CourseService(CourseRepository courseRepository,
                         EnrollmentRepository enrollmentRepository,
                         StringRedisTemplate redisTemplate) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.redisTemplate = redisTemplate;
    }

    public Page<CourseDto> listActiveCourses(Pageable pageable) {
        return courseRepository.findByIsActiveTrue(pageable)
                .map(CourseDto::fromEntity);
    }

    public Page<CourseDto> searchCourses(String query, Pageable pageable) {
        return courseRepository.searchByQuery(query, pageable)
                .map(CourseDto::fromEntity);
    }

    public Optional<CourseDto> getCourseById(String courseId) {
        return courseRepository.findById(courseId)
                .map(course -> {
                    long enrollments = enrollmentRepository.countByCourseId(courseId);
                    return CourseDto.fromEntityWithEnrollments(course, enrollments);
                });
    }

    /**
     * Add a new course and sync Redis seat cache.
     */
    @Transactional
    public CourseDto addCourse(CourseDto dto) {
        if (courseRepository.existsById(dto.getCourseId())) {
            throw new IllegalArgumentException("Course with ID " + dto.getCourseId() + " already exists");
        }

        Course course = new Course();
        course.setCourseId(dto.getCourseId());
        course.setCourseName(dto.getCourseName());
        course.setDepartment(dto.getDepartment());
        course.setDescription(dto.getDescription());
        course.setTotalCapacity(dto.getTotalCapacity());
        course.setAvailableSeats(dto.getTotalCapacity()); // New course starts with full capacity
        course.setActive(true);

        Course saved = courseRepository.save(course);

        // Sync Redis: set seat counter and initialize empty user set
        syncCourseToRedis(saved);

        log.info("Course created: {} (capacity={})", saved.getCourseId(), saved.getTotalCapacity());
        return CourseDto.fromEntity(saved);
    }

    /**
     * Soft-delete a course and remove from Redis cache.
     */
    @Transactional
    public boolean deleteCourse(String courseId) {
        Optional<Course> courseOpt = courseRepository.findById(courseId);
        if (courseOpt.isEmpty()) return false;

        Course course = courseOpt.get();
        course.setActive(false);
        courseRepository.save(course);

        // Remove from Redis
        redisTemplate.delete("course:" + courseId + ":seats");
        redisTemplate.delete("course:" + courseId + ":users");

        log.info("Course soft-deleted: {}", courseId);
        return true;
    }

    /**
     * Sync a course's seat data to Redis (used during seeding and admin CRUD).
     */
    public void syncCourseToRedis(Course course) {
        String seatsKey = "course:" + course.getCourseId() + ":seats";
        redisTemplate.opsForValue().set(seatsKey, String.valueOf(course.getAvailableSeats()));
        log.debug("Redis synced: {} = {}", seatsKey, course.getAvailableSeats());
    }
}
