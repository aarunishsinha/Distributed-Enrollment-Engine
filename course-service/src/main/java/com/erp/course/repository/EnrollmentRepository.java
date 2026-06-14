package com.erp.course.repository;

import com.erp.course.model.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    long count();

    long countByCourseId(String courseId);

    List<Enrollment> findByUserId(String userId);

    @Query("SELECT e.courseId, COUNT(e) FROM Enrollment e GROUP BY e.courseId ORDER BY COUNT(e) DESC")
    List<Object[]> countByCourse();

    @Query("SELECT e.courseId, COUNT(e) FROM Enrollment e GROUP BY e.courseId ORDER BY COUNT(e) DESC LIMIT 10")
    List<Object[]> topCoursesByEnrollment();

    @Query("SELECT CAST(e.createdAt AS date), COUNT(e) FROM Enrollment e " +
           "WHERE e.createdAt >= :since GROUP BY CAST(e.createdAt AS date) ORDER BY CAST(e.createdAt AS date)")
    List<Object[]> enrollmentTimeseries(@Param("since") LocalDateTime since);

    @Query("SELECT c.department, COUNT(e) FROM Enrollment e JOIN Course c ON e.courseId = c.courseId " +
           "GROUP BY c.department ORDER BY COUNT(e) DESC")
    List<Object[]> enrollmentsByDepartment();
}
