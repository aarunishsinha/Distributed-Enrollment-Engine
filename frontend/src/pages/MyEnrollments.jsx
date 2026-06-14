import { useState, useEffect } from 'react';
import { getUserEnrollments } from '../services/api';

export default function MyEnrollments({ addToast }) {
  const [enrollments, setEnrollments] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchEnrollments();
  }, []);

  const fetchEnrollments = async () => {
    setLoading(true);
    try {
      const userId = localStorage.getItem('currentUserId') || 'STU-00001';
      const data = await getUserEnrollments(userId);
      setEnrollments(data || []);
    } catch (err) {
      addToast('Failed to load enrollments', 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">My Enrollments</h1>
        <p className="page-subtitle">Your current course registrations</p>
      </div>

      {loading ? (
        <div className="loading-spinner"><div className="spinner" /></div>
      ) : enrollments.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">🎓</div>
          <p className="empty-state-text">No enrollments yet — head to the catalog to enroll!</p>
        </div>
      ) : (
        <div className="data-table-container">
          <div className="data-table-header">
            <span className="data-table-title">Enrolled Courses ({enrollments.length})</span>
          </div>
          <table className="data-table">
            <thead>
              <tr>
                <th>Course ID</th>
                <th>Status</th>
                <th>Enrolled At</th>
              </tr>
            </thead>
            <tbody>
              {enrollments.map(e => (
                <tr key={e.enrollmentId}>
                  <td style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{e.courseId}</td>
                  <td>
                    <span className="badge badge-active">{e.status}</span>
                  </td>
                  <td style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                    {e.createdAt ? new Date(e.createdAt).toLocaleString() : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
