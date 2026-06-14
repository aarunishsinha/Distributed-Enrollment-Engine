import { useState, useEffect } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  AreaChart, Area, PieChart, Pie, Cell
} from 'recharts';
import { getAnalytics, addCourse, deleteCourse, listUsers, addUser, deleteUser } from '../services/api';

const CHART_COLORS = ['#6366f1', '#8b5cf6', '#a78bfa', '#c4b5fd', '#818cf8', '#6d28d9'];

export default function AdminDashboard({ addToast }) {
  const [analytics, setAnalytics] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('overview');
  const [users, setUsers] = useState([]);
  const [usersPage, setUsersPage] = useState(0);
  const [usersTotalPages, setUsersTotalPages] = useState(0);
  const [showAddCourse, setShowAddCourse] = useState(false);
  const [showAddUser, setShowAddUser] = useState(false);
  const [newCourse, setNewCourse] = useState({ courseId: '', courseName: '', department: '', totalCapacity: '' });
  const [newUser, setNewUser] = useState({ userId: '', username: '', email: '', role: 'STUDENT' });

  useEffect(() => {
    fetchAnalytics();
  }, []);

  useEffect(() => {
    if (activeTab === 'users') fetchUsers();
  }, [activeTab, usersPage]);

  const fetchAnalytics = async () => {
    setLoading(true);
    try {
      const data = await getAnalytics();
      setAnalytics(data);
    } catch (err) {
      addToast('Failed to load analytics: ' + (err.message || ''), 'error');
    } finally {
      setLoading(false);
    }
  };

  const fetchUsers = async () => {
    try {
      const data = await listUsers(usersPage, 20);
      setUsers(data.content || []);
      setUsersTotalPages(data.totalPages || 0);
    } catch (err) {
      addToast('Failed to load users', 'error');
    }
  };

  const handleAddCourse = async (e) => {
    e.preventDefault();
    try {
      await addCourse({ ...newCourse, totalCapacity: parseInt(newCourse.totalCapacity) });
      addToast(`Course ${newCourse.courseId} created`, 'success');
      setShowAddCourse(false);
      setNewCourse({ courseId: '', courseName: '', department: '', totalCapacity: '' });
      fetchAnalytics();
    } catch (err) {
      addToast(err.message || 'Failed to create course', 'error');
    }
  };

  const handleDeleteCourse = async (courseId) => {
    if (!confirm(`Remove course ${courseId}?`)) return;
    try {
      await deleteCourse(courseId);
      addToast(`Course ${courseId} removed`, 'success');
      fetchAnalytics();
    } catch (err) {
      addToast(err.message || 'Failed to remove course', 'error');
    }
  };

  const handleAddUser = async (e) => {
    e.preventDefault();
    try {
      await addUser(newUser);
      addToast(`User ${newUser.userId} created`, 'success');
      setShowAddUser(false);
      setNewUser({ userId: '', username: '', email: '', role: 'STUDENT' });
      fetchUsers();
    } catch (err) {
      addToast(err.message || 'Failed to create user', 'error');
    }
  };

  const handleDeleteUser = async (userId) => {
    if (!confirm(`Remove user ${userId}?`)) return;
    try {
      await deleteUser(userId);
      addToast(`User ${userId} removed`, 'success');
      fetchUsers();
    } catch (err) {
      addToast(err.message || 'Failed to remove user', 'error');
    }
  };

  if (loading) {
    return <div className="loading-spinner"><div className="spinner" /></div>;
  }

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">Admin Dashboard</h1>
        <p className="page-subtitle">Enrollment analytics & system management</p>
      </div>

      {/* Tab Navigation */}
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '2rem' }}>
        {['overview', 'courses', 'users'].map(tab => (
          <button
            key={tab}
            className={`btn ${activeTab === tab ? 'btn-primary' : 'btn-ghost'} btn-sm`}
            onClick={() => setActiveTab(tab)}
          >
            {tab === 'overview' ? '📊 Overview' : tab === 'courses' ? '📚 Courses' : '👤 Users'}
          </button>
        ))}
      </div>

      {/* ─── Overview Tab ─── */}
      {activeTab === 'overview' && analytics && (
        <>
          <div className="stats-grid">
            <div className="stat-card purple">
              <div className="stat-label">Total Courses</div>
              <div className="stat-value purple">{analytics.activeCourses?.toLocaleString()}</div>
              <div className="stat-change">{analytics.totalCourses} total (incl. inactive)</div>
            </div>
            <div className="stat-card green">
              <div className="stat-label">Total Enrollments</div>
              <div className="stat-value green">{analytics.totalEnrollments?.toLocaleString()}</div>
            </div>
            <div className="stat-card blue">
              <div className="stat-label">Total Capacity</div>
              <div className="stat-value blue">{analytics.totalCapacity?.toLocaleString()}</div>
            </div>
            <div className="stat-card orange">
              <div className="stat-label">Utilization</div>
              <div className="stat-value orange">{analytics.utilizationPercent}%</div>
            </div>
          </div>

          <div className="charts-row">
            {/* Department breakdown */}
            <div className="chart-container">
              <h3 className="chart-title">Enrollments by Department</h3>
              {analytics.departmentBreakdown?.length > 0 ? (
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={analytics.departmentBreakdown.slice(0, 8)}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.1)" />
                    <XAxis
                      dataKey="department"
                      tick={{ fill: '#94a3b8', fontSize: 11 }}
                      angle={-25}
                      textAnchor="end"
                      height={60}
                    />
                    <YAxis tick={{ fill: '#94a3b8', fontSize: 11 }} />
                    <Tooltip
                      contentStyle={{ background: '#1a1f35', border: '1px solid rgba(148,163,184,0.2)', borderRadius: 8, color: '#f1f5f9' }}
                    />
                    <Bar dataKey="enrollments" radius={[6, 6, 0, 0]}>
                      {(analytics.departmentBreakdown || []).slice(0, 8).map((_, i) => (
                        <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <div className="empty-state"><p className="empty-state-text">No enrollment data yet</p></div>
              )}
            </div>

            {/* Top courses pie */}
            <div className="chart-container">
              <h3 className="chart-title">Top Courses</h3>
              {analytics.topCourses?.length > 0 ? (
                <ResponsiveContainer width="100%" height={300}>
                  <PieChart>
                    <Pie
                      data={analytics.topCourses.slice(0, 6)}
                      dataKey="enrollments"
                      nameKey="courseId"
                      cx="50%"
                      cy="50%"
                      outerRadius={100}
                      label={({ courseId, enrollments }) => `${courseId}: ${enrollments}`}
                      labelLine={{ stroke: '#64748b' }}
                    >
                      {analytics.topCourses.slice(0, 6).map((_, i) => (
                        <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{ background: '#1a1f35', border: '1px solid rgba(148,163,184,0.2)', borderRadius: 8, color: '#f1f5f9' }}
                    />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <div className="empty-state"><p className="empty-state-text">No enrollment data yet</p></div>
              )}
            </div>
          </div>

          {/* Time series */}
          {analytics.timeseries?.length > 0 && (
            <div className="chart-container">
              <h3 className="chart-title">Enrollment Trend (Last 30 Days)</h3>
              <ResponsiveContainer width="100%" height={250}>
                <AreaChart data={analytics.timeseries}>
                  <defs>
                    <linearGradient id="gradEnroll" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#6366f1" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="#6366f1" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.1)" />
                  <XAxis dataKey="date" tick={{ fill: '#94a3b8', fontSize: 11 }} />
                  <YAxis tick={{ fill: '#94a3b8', fontSize: 11 }} />
                  <Tooltip
                    contentStyle={{ background: '#1a1f35', border: '1px solid rgba(148,163,184,0.2)', borderRadius: 8, color: '#f1f5f9' }}
                  />
                  <Area type="monotone" dataKey="enrollments" stroke="#6366f1" fill="url(#gradEnroll)" strokeWidth={2} />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}
        </>
      )}

      {/* ─── Courses Tab ─── */}
      {activeTab === 'courses' && (
        <div className="data-table-container">
          <div className="data-table-header">
            <span className="data-table-title">Course Management</span>
            <button className="btn btn-primary btn-sm" onClick={() => setShowAddCourse(true)}>
              + Add Course
            </button>
          </div>
          {analytics?.topCourses?.length > 0 ? (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Course ID</th>
                  <th>Enrollments</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {analytics.topCourses.map(c => (
                  <tr key={c.courseId}>
                    <td style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{c.courseId}</td>
                    <td>{c.enrollments}</td>
                    <td>
                      <button
                        className="btn btn-danger btn-sm"
                        onClick={() => handleDeleteCourse(c.courseId)}
                      >
                        Remove
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <div className="empty-state">
              <p className="empty-state-text">No courses yet</p>
            </div>
          )}
        </div>
      )}

      {/* ─── Users Tab ─── */}
      {activeTab === 'users' && (
        <div className="data-table-container">
          <div className="data-table-header">
            <span className="data-table-title">User Management</span>
            <button className="btn btn-primary btn-sm" onClick={() => setShowAddUser(true)}>
              + Add User
            </button>
          </div>
          {users.length > 0 ? (
            <>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>User ID</th>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Role</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map(u => (
                    <tr key={u.userId}>
                      <td style={{ fontWeight: 600, color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}>
                        {u.userId}
                      </td>
                      <td>{u.username}</td>
                      <td style={{ color: 'var(--text-muted)' }}>{u.email}</td>
                      <td>
                        <span className={`badge ${u.role === 'ADMIN' ? 'badge-admin' : 'badge-student'}`}>
                          {u.role}
                        </span>
                      </td>
                      <td>
                        <button
                          className="btn btn-danger btn-sm"
                          onClick={() => handleDeleteUser(u.userId)}
                          disabled={u.userId === 'ADMIN-001'}
                        >
                          Remove
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {usersTotalPages > 1 && (
                <div style={{ display: 'flex', justifyContent: 'center', gap: '0.5rem', padding: '1rem' }}>
                  <button className="btn btn-ghost btn-sm" disabled={usersPage === 0} onClick={() => setUsersPage(p => p - 1)}>← Prev</button>
                  <span style={{ display: 'flex', alignItems: 'center', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
                    {usersPage + 1}/{usersTotalPages}
                  </span>
                  <button className="btn btn-ghost btn-sm" disabled={usersPage >= usersTotalPages - 1} onClick={() => setUsersPage(p => p + 1)}>Next →</button>
                </div>
              )}
            </>
          ) : (
            <div className="empty-state"><p className="empty-state-text">No users found</p></div>
          )}
        </div>
      )}

      {/* ─── Add Course Modal ─── */}
      {showAddCourse && (
        <div className="modal-overlay" onClick={() => setShowAddCourse(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h2 className="modal-title">Add New Course</h2>
            <form onSubmit={handleAddCourse}>
              <div className="form-group">
                <label className="form-label">Course ID</label>
                <input className="form-input" required placeholder="e.g. CS-601"
                  value={newCourse.courseId}
                  onChange={e => setNewCourse(prev => ({ ...prev, courseId: e.target.value }))}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Course Name</label>
                <input className="form-input" required placeholder="e.g. Advanced Algorithms"
                  value={newCourse.courseName}
                  onChange={e => setNewCourse(prev => ({ ...prev, courseName: e.target.value }))}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Department</label>
                <input className="form-input" placeholder="e.g. Computer Science"
                  value={newCourse.department}
                  onChange={e => setNewCourse(prev => ({ ...prev, department: e.target.value }))}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Capacity</label>
                <input className="form-input" type="number" required min="1" placeholder="e.g. 100"
                  value={newCourse.totalCapacity}
                  onChange={e => setNewCourse(prev => ({ ...prev, totalCapacity: e.target.value }))}
                />
              </div>
              <div className="form-actions">
                <button type="button" className="btn btn-ghost" onClick={() => setShowAddCourse(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Create Course</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ─── Add User Modal ─── */}
      {showAddUser && (
        <div className="modal-overlay" onClick={() => setShowAddUser(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h2 className="modal-title">Add New User</h2>
            <form onSubmit={handleAddUser}>
              <div className="form-group">
                <label className="form-label">User ID</label>
                <input className="form-input" required placeholder="e.g. STU-00010"
                  value={newUser.userId}
                  onChange={e => setNewUser(prev => ({ ...prev, userId: e.target.value }))}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Username</label>
                <input className="form-input" required placeholder="e.g. Jane Doe"
                  value={newUser.username}
                  onChange={e => setNewUser(prev => ({ ...prev, username: e.target.value }))}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Email</label>
                <input className="form-input" type="email" required placeholder="e.g. jane@university.edu"
                  value={newUser.email}
                  onChange={e => setNewUser(prev => ({ ...prev, email: e.target.value }))}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Role</label>
                <select className="form-input"
                  value={newUser.role}
                  onChange={e => setNewUser(prev => ({ ...prev, role: e.target.value }))}>
                  <option value="STUDENT">Student</option>
                  <option value="ADMIN">Admin</option>
                </select>
              </div>
              <div className="form-actions">
                <button type="button" className="btn btn-ghost" onClick={() => setShowAddUser(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Create User</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
