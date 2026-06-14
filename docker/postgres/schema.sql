-- ============================================
-- University ERP — Database Schema
-- PostgreSQL 15
-- ============================================

-- Users table
CREATE TABLE users (
    user_id     VARCHAR(50) PRIMARY KEY,
    username    VARCHAR(100) NOT NULL,
    email       VARCHAR(150) UNIQUE NOT NULL,
    role        VARCHAR(20) NOT NULL DEFAULT 'STUDENT',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_role CHECK (role IN ('STUDENT', 'ADMIN'))
);

-- Courses table
CREATE TABLE courses (
    course_id       VARCHAR(50) PRIMARY KEY,
    course_name     VARCHAR(255) NOT NULL,
    department      VARCHAR(100),
    description     TEXT,
    total_capacity  INT NOT NULL,
    available_seats INT NOT NULL,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Enrollments table
CREATE TABLE enrollments (
    enrollment_id   SERIAL PRIMARY KEY,
    course_id       VARCHAR(50) REFERENCES courses(course_id),
    user_id         VARCHAR(50) REFERENCES users(user_id),
    status          VARCHAR(20) DEFAULT 'CONFIRMED',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(course_id, user_id)
);

-- Indexes
CREATE INDEX idx_courses_name ON courses(course_name);
CREATE INDEX idx_courses_active ON courses(is_active);
CREATE INDEX idx_enrollments_course_id ON enrollments(course_id);
CREATE INDEX idx_enrollments_user_id ON enrollments(user_id);
CREATE INDEX idx_enrollments_created_at ON enrollments(created_at);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- Seed admin user
INSERT INTO users (user_id, username, email, role) VALUES
    ('ADMIN-001', 'System Admin', 'admin@university.edu', 'ADMIN');

-- Seed sample students
INSERT INTO users (user_id, username, email, role) VALUES
    ('STU-00001', 'Alice Chen', 'alice.chen@university.edu', 'STUDENT'),
    ('STU-00002', 'Bob Martinez', 'bob.martinez@university.edu', 'STUDENT'),
    ('STU-00003', 'Charlie Kim', 'charlie.kim@university.edu', 'STUDENT'),
    ('STU-00004', 'Diana Patel', 'diana.patel@university.edu', 'STUDENT'),
    ('STU-00005', 'Ethan Okonkwo', 'ethan.okonkwo@university.edu', 'STUDENT');

-- Triggers to automatically sync available_seats in courses table
CREATE OR REPLACE FUNCTION decrement_course_seats()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE courses 
    SET available_seats = available_seats - 1 
    WHERE course_id = NEW.course_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_decrement_course_seats
AFTER INSERT ON enrollments
FOR EACH ROW
EXECUTE FUNCTION decrement_course_seats();

CREATE OR REPLACE FUNCTION increment_course_seats()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE courses 
    SET available_seats = available_seats + 1 
    WHERE course_id = OLD.course_id;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_increment_course_seats
AFTER DELETE ON enrollments
FOR EACH ROW
EXECUTE FUNCTION increment_course_seats();

