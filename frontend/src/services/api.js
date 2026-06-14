const API_BASE = '/api/v1';

async function request(path, options = {}) {
  const userId = localStorage.getItem('currentUserId') || 'STU-00001';
  const userRole = localStorage.getItem('currentUserRole') || 'STUDENT';

  const headers = {
    'Content-Type': 'application/json',
    'X-User-Id': userId,
    'X-User-Role': userRole,
    ...options.headers,
  };

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers });
  
  if (res.status === 204) return null;
  
  const data = await res.json().catch(() => null);

  if (!res.ok) {
    const error = new Error(data?.error || `Request failed with status ${res.status}`);
    error.status = res.status;
    error.data = data;
    throw error;
  }

  return data;
}

// ─── Courses ────────────────────────────────────────────────
export function listCourses(page = 0, size = 20) {
  return request(`/courses?page=${page}&size=${size}`);
}

export function searchCourses(query, page = 0, size = 20) {
  return request(`/courses/search?q=${encodeURIComponent(query)}&page=${page}&size=${size}`);
}

export function getCourse(courseId) {
  return request(`/courses/${encodeURIComponent(courseId)}`);
}

export function addCourse(course) {
  return request('/courses', {
    method: 'POST',
    body: JSON.stringify(course),
  });
}

export function deleteCourse(courseId) {
  return request(`/courses/${encodeURIComponent(courseId)}`, {
    method: 'DELETE',
  });
}

// ─── Enrollments ────────────────────────────────────────────
export function enroll(courseId) {
  const idempotencyKey = crypto.randomUUID();
  return request('/enrollments', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ courseId }),
  });
}

export function getUserEnrollments(userId) {
  return request(`/users/${encodeURIComponent(userId)}/enrollments`);
}

// ─── Admin ──────────────────────────────────────────────────
export function getAnalytics() {
  return request('/admin/analytics');
}

export function listUsers(page = 0, size = 20) {
  return request(`/admin/users?page=${page}&size=${size}`);
}

export function addUser(user) {
  return request('/admin/users', {
    method: 'POST',
    body: JSON.stringify(user),
  });
}

export function deleteUser(userId) {
  return request(`/admin/users/${encodeURIComponent(userId)}`, {
    method: 'DELETE',
  });
}
