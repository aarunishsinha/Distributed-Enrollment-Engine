CREATE TABLE courses (
    course_id VARCHAR(50) PRIMARY KEY,
    total_capacity INT NOT NULL,
    available_seats INT NOT NULL
);

CREATE TABLE enrollments (
    enrollment_id SERIAL PRIMARY KEY,
    course_id VARCHAR(50) REFERENCES courses(course_id),
    user_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'CONFIRMED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(course_id, user_id) 
);

-- Seed Data
INSERT INTO courses (course_id, total_capacity, available_seats) VALUES ('CS400', 50, 50);
