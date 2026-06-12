package com.erp.course.repository;

import com.erp.course.model.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, String> {

    Page<Course> findByIsActiveTrue(Pageable pageable);

    @Query("SELECT c FROM Course c WHERE c.isActive = true AND " +
           "(LOWER(c.courseName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.courseId) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Course> searchByQuery(@Param("query") String query, Pageable pageable);

    long countByIsActiveTrue();

    @Query("SELECT c.department, COUNT(c) FROM Course c WHERE c.isActive = true GROUP BY c.department ORDER BY COUNT(c) DESC")
    List<Object[]> countByDepartment();
}
