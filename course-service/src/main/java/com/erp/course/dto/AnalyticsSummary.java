package com.erp.course.dto;

import java.util.List;
import java.util.Map;

/**
 * Analytics summary returned by the admin analytics endpoint.
 */
public class AnalyticsSummary {

    private long totalCourses;
    private long activeCourses;
    private long totalEnrollments;
    private long totalCapacity;
    private double utilizationPercent;
    private List<Map<String, Object>> topCourses;
    private List<Map<String, Object>> departmentBreakdown;
    private List<Map<String, Object>> timeseries;

    // Getters and setters
    public long getTotalCourses() { return totalCourses; }
    public void setTotalCourses(long totalCourses) { this.totalCourses = totalCourses; }

    public long getActiveCourses() { return activeCourses; }
    public void setActiveCourses(long activeCourses) { this.activeCourses = activeCourses; }

    public long getTotalEnrollments() { return totalEnrollments; }
    public void setTotalEnrollments(long totalEnrollments) { this.totalEnrollments = totalEnrollments; }

    public long getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(long totalCapacity) { this.totalCapacity = totalCapacity; }

    public double getUtilizationPercent() { return utilizationPercent; }
    public void setUtilizationPercent(double utilizationPercent) { this.utilizationPercent = utilizationPercent; }

    public List<Map<String, Object>> getTopCourses() { return topCourses; }
    public void setTopCourses(List<Map<String, Object>> topCourses) { this.topCourses = topCourses; }

    public List<Map<String, Object>> getDepartmentBreakdown() { return departmentBreakdown; }
    public void setDepartmentBreakdown(List<Map<String, Object>> departmentBreakdown) { this.departmentBreakdown = departmentBreakdown; }

    public List<Map<String, Object>> getTimeseries() { return timeseries; }
    public void setTimeseries(List<Map<String, Object>> timeseries) { this.timeseries = timeseries; }
}
