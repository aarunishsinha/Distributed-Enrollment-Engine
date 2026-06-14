import { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, NavLink, useLocation } from 'react-router-dom';
import CourseCatalog from './pages/CourseCatalog';
import AdminDashboard from './pages/AdminDashboard';
import MyEnrollments from './pages/MyEnrollments';
import { ToastContainer, useToast } from './components/Toast';
import './index.css';

const USERS = [
  { id: 'STU-00001', name: 'Alice Chen', role: 'STUDENT' },
  { id: 'STU-00002', name: 'Bob Martinez', role: 'STUDENT' },
  { id: 'STU-00003', name: 'Charlie Kim', role: 'STUDENT' },
  { id: 'STU-00004', name: 'Diana Patel', role: 'STUDENT' },
  { id: 'STU-00005', name: 'Ethan Okonkwo', role: 'STUDENT' },
  { id: 'ADMIN-001', name: 'System Admin', role: 'ADMIN' },
];

function AppContent() {
  const { toasts, addToast } = useToast();
  const [currentUser, setCurrentUser] = useState(USERS[0]);
  const location = useLocation();

  useEffect(() => {
    localStorage.setItem('currentUserId', currentUser.id);
    localStorage.setItem('currentUserRole', currentUser.role);
  }, [currentUser]);

  const handleUserChange = (e) => {
    const user = USERS.find(u => u.id === e.target.value);
    if (user) {
      setCurrentUser(user);
      addToast(`Switched to ${user.name} (${user.role})`, 'info');
    }
  };

  const isAdmin = currentUser.role === 'ADMIN';

  return (
    <div className="app-layout">
      <ToastContainer toasts={toasts} />

      {/* Sidebar */}
      <aside className="sidebar">
        <div className="sidebar-header">
          <div className="sidebar-logo">
            <div className="sidebar-logo-icon">U</div>
            <div>
              <div className="sidebar-logo-text">UniERP</div>
              <div className="sidebar-logo-badge">Enrollment Engine</div>
            </div>
          </div>
        </div>

        <nav className="sidebar-nav">
          <div className="nav-section-label">Student</div>
          <NavLink to="/" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`} end>
            <span className="nav-link-icon">📚</span>
            Course Catalog
          </NavLink>
          <NavLink to="/enrollments" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <span className="nav-link-icon">🎓</span>
            My Enrollments
          </NavLink>

          {isAdmin && (
            <>
              <div className="nav-section-label" style={{ marginTop: '1rem' }}>Administration</div>
              <NavLink to="/admin" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
                <span className="nav-link-icon">📊</span>
                Dashboard
              </NavLink>
            </>
          )}
        </nav>

        <div className="sidebar-footer">
          <div className="nav-section-label" style={{ padding: '0 0 0.4rem' }}>Switch User</div>
          <select className="user-select" value={currentUser.id} onChange={handleUserChange}>
            {USERS.map(u => (
              <option key={u.id} value={u.id}>
                {u.name} ({u.role})
              </option>
            ))}
          </select>
        </div>
      </aside>

      {/* Main */}
      <main className="main-content">
        <Routes>
          <Route path="/" element={<CourseCatalog addToast={addToast} />} />
          <Route path="/enrollments" element={<MyEnrollments addToast={addToast} />} />
          <Route path="/admin" element={
            isAdmin
              ? <AdminDashboard addToast={addToast} />
              : <div className="empty-state">
                  <div className="empty-state-icon">🔒</div>
                  <p className="empty-state-text">Admin access required</p>
                </div>
          } />
        </Routes>
      </main>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AppContent />
    </BrowserRouter>
  );
}
