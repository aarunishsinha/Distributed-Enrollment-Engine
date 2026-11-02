#!/usr/bin/env bash
# Reset a course to full capacity and clear per-run Redis state before a benchmark.
# Usage: benchmarks/reset-course.sh <COURSE_ID>
set -euo pipefail

COURSE_ID="${1:?usage: $0 <COURSE_ID>}"
PG=(docker exec -i erp_postgres psql -U postgres -d enrollment_db -qtA -v ON_ERROR_STOP=1)
REDIS=(docker exec -i erp_redis redis-cli)

count_enrollments() {
    "${PG[@]}" -c "SELECT count(*) FROM enrollments WHERE course_id = '$COURSE_ID';"
}

# Wait for the worker to finish draining Kafka so late inserts can't land after the reset
prev=-1
curr=$(count_enrollments)
while [ "$curr" != "$prev" ]; do
    prev=$curr
    sleep 3
    curr=$(count_enrollments)
done

capacity=$("${PG[@]}" -c "SELECT total_capacity FROM courses WHERE course_id = '$COURSE_ID';")
if [ -z "$capacity" ]; then
    echo "Course $COURSE_ID not found" >&2
    exit 1
fi

# Delete fires trg_increment_course_seats; the UPDATE is a safety net
"${PG[@]}" <<SQL
BEGIN;
DELETE FROM enrollments WHERE course_id = '$COURSE_ID';
UPDATE courses SET available_seats = total_capacity WHERE course_id = '$COURSE_ID';
COMMIT;
SQL

"${REDIS[@]}" SET "course:$COURSE_ID:seats" "$capacity" >/dev/null
"${REDIS[@]}" DEL "course:$COURSE_ID:users" >/dev/null

# Idempotency and rate-limit keys are per run; clear them so every run starts with the same Redis memory
for pattern in 'idempotency:*' 'rate_limit:*'; do
    "${REDIS[@]}" --scan --pattern "$pattern" --count 1000 | xargs -n 1000 docker exec -i erp_redis redis-cli UNLINK >/dev/null 2>&1 || true
done

echo "Reset $COURSE_ID: seats=$capacity, enrollments=$(count_enrollments), redis keys=$("${REDIS[@]}" DBSIZE)"
