#!/usr/bin/env bash
# Run one benchmark: reset the course, sample docker stats every second, run k6.
# Usage: benchmarks/run.sh <bucket>/<run> [k6 -e args...]
#   e.g. benchmarks/run.sh reject-path/run-b -e MODE=breakpoint -e PEAK=1500 -e COURSE_ID=FLASH-100
# Writes into benchmarks/results/<bucket>/<run>/: <run>.txt, <run>-stats.csv (docker stats)
# and <run>-k6.csv.gz (per-request metrics)
set -euo pipefail

NAME="${1:?usage: $0 <bucket>/<run> [k6 -e args...]}"
shift

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/benchmarks/results/$NAME"
RUN="$(basename "$NAME")"
mkdir -p "$OUT"

# Course to reset: COURSE_ID from the -e args, else the script default
COURSE_ID=CS-101
for arg in "$@"; do
    case "$arg" in COURSE_ID=*) COURSE_ID="${arg#COURSE_ID=}" ;; esac
done

"$ROOT/benchmarks/reset-course.sh" "$COURSE_ID"

STATS="$OUT/$RUN-stats.csv"
echo "time,container,cpu_perc,mem_usage,net_io,block_io,pids" > "$STATS"
(
    while true; do
        ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)
        docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.NetIO}},{{.BlockIO}},{{.PIDs}}' \
            | sed "s/^/$ts,/" >> "$STATS"
        sleep 1
    done
) &
STATS_PID=$!
trap 'kill $STATS_PID 2>/dev/null || true' EXIT

# k6 exits non-zero when a breakpoint threshold aborts the run; keep the output either way
k6 run "$@" --summary-trend-stats="avg,p(50),p(95),p(99),max" \
    --out "csv=$OUT/$RUN-k6.csv.gz" \
    "$ROOT/tests/load/flash-sale-10k.js" | tee "$OUT/$RUN.txt" || true
