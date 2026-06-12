package com.erp.course.seed;

import com.erp.course.model.Course;
import com.erp.course.repository.CourseRepository;
import com.erp.course.service.CourseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the courses table from the Harvard Course Enrollment CSV on first boot.
 * Only runs if the courses table is empty (idempotent).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CourseRepository courseRepository;
    private final CourseService courseService;

    public DataSeeder(CourseRepository courseRepository, CourseService courseService) {
        this.courseRepository = courseRepository;
        this.courseService = courseService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (courseRepository.count() > 0) {
            log.info("Courses table already seeded ({} courses), skipping.", courseRepository.count());
            return;
        }

        log.info("Seeding courses from Harvard dataset...");
        ClassPathResource resource = new ClassPathResource("data/harvard_courses.csv");

        if (!resource.exists()) {
            log.warn("Harvard dataset CSV not found at classpath:data/harvard_courses.csv — generating sample courses");
            seedSampleCourses();
            return;
        }

        List<Course> courses = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine(); // Skip header
            if (header == null) {
                log.warn("CSV file is empty");
                return;
            }

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                try {
                    Course course = parseCsvLine(line);
                    if (course != null) {
                        courses.add(course);
                        count++;
                    }
                } catch (Exception e) {
                    log.warn("Skipping malformed CSV line: {}", line);
                }
            }

            courseRepository.saveAll(courses);

            // Sync all courses to Redis
            for (Course course : courses) {
                courseService.syncCourseToRedis(course);
            }

            log.info("Successfully seeded {} courses and synced to Redis", count);
        }
    }

    private Course parseCsvLine(String line) {
        // Handle CSV with potential commas in quoted fields
        String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

        if (parts.length < 2) return null;

        String courseName = parts[0].trim().replaceAll("^\"|\"$", "");
        String enrollmentStr = parts.length > 1 ? parts[1].trim().replaceAll("^\"|\"$", "") : "50";

        // Generate a course ID from the name
        String courseId = generateCourseId(courseName);
        if (courseId.length() > 50) courseId = courseId.substring(0, 50);

        int enrollment;
        try {
            enrollment = Integer.parseInt(enrollmentStr.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            enrollment = 50; // Default
        }

        // Capacity = enrollment * 1.2, rounded up, minimum 10
        int capacity = Math.max(10, (int) Math.ceil(enrollment * 1.2));

        Course course = new Course();
        course.setCourseId(courseId);
        course.setCourseName(courseName);
        course.setDepartment(extractDepartment(courseName));
        course.setTotalCapacity(capacity);
        course.setAvailableSeats(capacity);
        course.setActive(true);

        return course;
    }

    private String generateCourseId(String name) {
        return name.toUpperCase()
                .replaceAll("[^A-Z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .substring(0, Math.min(name.length(), 45));
    }

    private String extractDepartment(String courseName) {
        // Simple heuristic: take first word(s) that look like a department
        String[] keywords = {"Computer Science", "Economics", "Mathematics", "Physics",
                "Chemistry", "Biology", "History", "English", "Psychology", "Statistics",
                "Philosophy", "Government", "Sociology", "Music", "Art"};

        String upper = courseName.toUpperCase();
        for (String kw : keywords) {
            if (upper.contains(kw.toUpperCase())) return kw;
        }

        // Fallback: first word
        String[] words = courseName.split("\\s+");
        return words.length > 0 ? words[0] : "General";
    }

    /**
     * Fallback: generate sample courses if Harvard CSV is not available.
     */
    private void seedSampleCourses() {
        String[][] sampleCourses = {
                {"CS-101", "Introduction to Computer Science", "Computer Science", "120"},
                {"CS-201", "Data Structures and Algorithms", "Computer Science", "100"},
                {"CS-301", "Operating Systems", "Computer Science", "80"},
                {"CS-401", "Distributed Systems", "Computer Science", "60"},
                {"CS-501", "Machine Learning", "Computer Science", "90"},
                {"MATH-101", "Calculus I", "Mathematics", "200"},
                {"MATH-201", "Linear Algebra", "Mathematics", "150"},
                {"MATH-301", "Probability and Statistics", "Mathematics", "120"},
                {"PHYS-101", "Introduction to Physics", "Physics", "180"},
                {"PHYS-201", "Quantum Mechanics", "Physics", "70"},
                {"ECON-101", "Principles of Economics", "Economics", "250"},
                {"ECON-201", "Microeconomics", "Economics", "150"},
                {"ECON-301", "Macroeconomics", "Economics", "130"},
                {"HIST-101", "World History", "History", "200"},
                {"HIST-201", "Modern European History", "History", "100"},
                {"ENG-101", "English Composition", "English", "180"},
                {"ENG-201", "American Literature", "English", "120"},
                {"PSYCH-101", "Introduction to Psychology", "Psychology", "220"},
                {"BIO-101", "Introduction to Biology", "Biology", "200"},
                {"CHEM-101", "General Chemistry", "Chemistry", "180"},
        };

        List<Course> courses = new ArrayList<>();
        for (String[] data : sampleCourses) {
            Course course = new Course();
            course.setCourseId(data[0]);
            course.setCourseName(data[1]);
            course.setDepartment(data[2]);
            course.setTotalCapacity(Integer.parseInt(data[3]));
            course.setAvailableSeats(Integer.parseInt(data[3]));
            course.setActive(true);
            courses.add(course);
        }

        courseRepository.saveAll(courses);

        for (Course course : courses) {
            courseService.syncCourseToRedis(course);
        }

        log.info("Seeded {} sample courses", courses.size());
    }
}
