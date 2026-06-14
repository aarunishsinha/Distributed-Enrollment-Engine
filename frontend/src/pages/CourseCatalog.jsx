import { useState, useEffect, useCallback } from 'react';
import { listCourses, searchCourses, enroll } from '../services/api';

export default function CourseCatalog({ addToast }) {
  const [courses, setCourses] = useState([]);
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [enrollingId, setEnrollingId] = useState(null);

  const fetchCourses = useCallback(async () => {
    setLoading(true);
    try {
      const data = query.trim()
        ? await searchCourses(query.trim(), page, 12)
        : await listCourses(page, 12);
      setCourses(data.content || []);
      setTotalPages(data.totalPages || 0);
    } catch (err) {
      addToast('Failed to load courses', 'error');
    } finally {
      setLoading(false);
    }
  }, [query, page, addToast]);

  useEffect(() => {
    const timer = setTimeout(fetchCourses, 300);
    return () => clearTimeout(timer);
  }, [fetchCourses]);

  const handleEnroll = async (courseId) => {
    setEnrollingId(courseId);
    try {
      await enroll(courseId);
      addToast(`Enrollment accepted for ${courseId}`, 'success');
      fetchCourses(); // Refresh to show updated seat counts
    } catch (err) {
      if (err.status === 409) {
        addToast('Already submitted — duplicate request blocked', 'warning');
      } else if (err.status === 429) {
        addToast(`Rate limited — retry in ${err.data?.retryAfter || 2}s`, 'warning');
      } else {
        addToast(err.message || 'Enrollment failed', 'error');
      }
    } finally {
      setEnrollingId(null);
    }
  };

  const getCapacityPercent = (course) => {
    if (course.totalCapacity === 0) return 0;
    return ((course.totalCapacity - course.availableSeats) / course.totalCapacity) * 100;
  };

  const getCapacityClass = (pct) => {
    if (pct >= 90) return 'high';
    if (pct >= 60) return 'medium';
    return 'low';
  };

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">Course Catalog</h1>
        <p className="page-subtitle">Search and enroll in available courses</p>
      </div>

      <div className="search-container">
        <span className="search-icon">🔍</span>
        <input
          type="text"
          className="search-input"
          placeholder="Search courses by name or ID..."
          value={query}
          onChange={(e) => { setQuery(e.target.value); setPage(0); }}
        />
      </div>

      {loading ? (
        <div className="loading-spinner"><div className="spinner" /></div>
      ) : courses.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">📚</div>
          <p className="empty-state-text">
            {query ? 'No courses match your search' : 'No courses available'}
          </p>
        </div>
      ) : (
        <>
          <div className="courses-grid">
            {courses.map(course => {
              const pct = getCapacityPercent(course);
              const isFull = course.availableSeats <= 0;
              return (
                <div key={course.courseId} className="course-card">
                  <div className="course-card-header">
                    <span className="course-id">{course.courseId}</span>
                    <span className="course-department">{course.department || 'General'}</span>
                  </div>
                  <h3 className="course-name">{course.courseName}</h3>
                  <div className="course-capacity">
                    <div className="capacity-bar">
                      <div
                        className={`capacity-fill ${getCapacityClass(pct)}`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                    <span className="capacity-text">
                      {course.totalCapacity - course.availableSeats}/{course.totalCapacity}
                    </span>
                  </div>
                  <div className="course-card-footer">
                    <span className={`badge ${isFull ? 'badge-inactive' : 'badge-active'}`}>
                      {isFull ? 'Full' : `${course.availableSeats} seats left`}
                    </span>
                    <button
                      className="btn btn-primary btn-sm"
                      disabled={isFull || enrollingId === course.courseId}
                      onClick={() => handleEnroll(course.courseId)}
                    >
                      {enrollingId === course.courseId ? '...' : isFull ? 'Full' : 'Enroll'}
                    </button>
                  </div>
                </div>
              );
            })}
          </div>

          {totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', gap: '0.5rem', marginTop: '2rem' }}>
              <button
                className="btn btn-ghost btn-sm"
                disabled={page === 0}
                onClick={() => setPage(p => p - 1)}
              >
                ← Previous
              </button>
              <span style={{ display: 'flex', alignItems: 'center', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
                Page {page + 1} of {totalPages}
              </span>
              <button
                className="btn btn-ghost btn-sm"
                disabled={page >= totalPages - 1}
                onClick={() => setPage(p => p + 1)}
              >
                Next →
              </button>
            </div>
          )}
        </>
      )}
    </>
  );
}
