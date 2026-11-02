#!/usr/bin/env bash
# Write-path benchmark: run.sh plus Kafka consumer lag, drain time and an end-to-end persistence check.
# Usage: benchmarks/write-path.sh <bucket>/<run> [k6 -e args...]
#   e.g. benchmarks/write-path.sh write-path/run-w -e MODE=constant -e RATE=1250 -e COURSE_ID=FLASH-BIG
# Writes everything run.sh writes, plus <run>-lag.txt and <run>-writes.txt in results/<bucket>/<run>/
set -euo pipefail

NAME="${1:?usage: $0 <bucket>/<run> [k6 -e args...]}"
shift

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/benchmarks/results/$NAME"
RUN="$(basename "$NAME")"
mkdir -p "$OUT"

COURSE_ID=CS-101
for arg in "$@"; do
    case "$arg" in COURSE_ID=*) COURSE_ID="${arg#COURSE_ID=}" ;; esac
done

# Total lag across partitions of enrollment_reserved; partitions with no committed offset ("-") are skipped
lag() {
    docker exec erp_kafka kafka-consumer-groups --bootstrap-server localhost:9092 \
        --describe --group enrollment-worker-group 2>/dev/null \
        | awk 'NR>1 && $6~/^[0-9]+$/{s+=$6} END{print s+0}'
}
dlq_count() {
    docker logs erp_worker 2>&1 | grep -c "Sent to DLQ" || true
}

dlq_before=$(dlq_count)

LAG_FILE="$OUT/$RUN-lag.txt"
: > "$LAG_FILE"
(
    while true; do
        echo "$(date +%T) $(lag)" >> "$LAG_FILE"
        sleep 2
    done
) &
LAG_PID=$!
trap 'kill $LAG_PID 2>/dev/null || true' EXIT

"$ROOT/benchmarks/run.sh" "$NAME" "$@"

k6_end=$(date +%s)
lag_at_end=$(lag)
echo "k6 finished at $(date +%T), lag=$lag_at_end; waiting for the worker to drain..."

# Give up after 10 minutes so a stuck worker can't hang the script
deadline=$((k6_end + 600))
current=$lag_at_end
while [ "$current" != "0" ] && [ "$(date +%s)" -lt "$deadline" ]; do
    sleep 1
    current=$(lag)
done
drain_secs=$(( $(date +%s) - k6_end ))

accepted=$(docker exec erp_redis redis-cli SCARD "course:$COURSE_ID:users")
persisted=$(docker exec erp_postgres psql -U postgres -d enrollment_db -tAc \
    "SELECT count(*) FROM enrollments WHERE course_id = '$COURSE_ID';")
dlq_after=$(dlq_count)

{
    echo "course:              $COURSE_ID"
    echo "lag at k6 end:       $lag_at_end"
    echo "final lag:           $current"
    echo "drain time:          ${drain_secs}s (k6 end -> lag 0)"
    if [ "$drain_secs" -gt 0 ] && [ "$lag_at_end" -gt 0 ]; then
        echo "drain rate:          $((lag_at_end / drain_secs)) events/s"
    fi
    echo "accepted (Redis):    $accepted"
    echo "persisted (Postgres): $persisted"
    echo "DLQ delta:           $((dlq_after - dlq_before))"
    if [ "$current" = "0" ] && [ "$accepted" = "$persisted" ] && [ "$dlq_after" = "$dlq_before" ]; then
        echo "result:              PASS (accepted == persisted, no DLQ)"
    else
        echo "result:              FAIL"
    fi
} | tee "$OUT/$RUN-writes.txt"
