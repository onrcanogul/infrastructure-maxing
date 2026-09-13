#!/usr/bin/env bash
# Sweeps request rates against both variants of the transaction-boundary lab and prints one
# line per run: achieved RPS, error rate, p95, and the most requests Hikari ever had waiting
# for a connection. Needs: docker compose --profile lab up -d --build
#
#   load/run-tx-boundary.sh "inside:20 40 50 60 80" "outside:50 100 200 400"
set -euo pipefail
cd "$(dirname "$0")/.."
NETWORK=infrastructure-maxing_default
DURATION=${DURATION:-30s}
OUT=load/results
mkdir -p "$OUT"

hikari() { # max of a Hikari gauge while the test runs, sampled from the app's Prometheus endpoint
  local metric=$1 file=$2
  while :; do
    curl -s localhost:8080/actuator/prometheus | awk -v m="$metric" '$1 ~ "^"m"\\{" {print $2}' >> "$file" || true
    sleep 0.5
  done
}

printf "%-8s %6s %9s %8s %9s %9s %8s %8s\n" variant rate achieved errors p50 p95 pending active
for spec in "$@"; do
  variant=${spec%%:*}
  for rate in ${spec#*:}; do
    pending=$(mktemp); active=$(mktemp)
    hikari hikaricp_connections_pending "$pending" & p1=$!
    hikari hikaricp_connections_active "$active" & p2=$!
    docker run --rm --network "$NETWORK" -v "$PWD/load:/load" \
      -e VARIANT="$variant" -e RATE="$rate" -e DURATION="$DURATION" \
      grafana/k6:1.3.0 run --quiet --summary-export "/load/results/$variant-$rate.json" /load/tx-boundary.js >/dev/null 2>&1 || true
    kill $p1 $p2 2>/dev/null; wait $p1 $p2 2>/dev/null || true
    python3 - "$OUT/$variant-$rate.json" "$variant" "$rate" "$pending" "$active" <<'PY'
import json, sys
path, variant, rate, pending, active = sys.argv[1:]
m = json.load(open(path))["metrics"]
def peak(f):
    vals = [float(x) for x in open(f).read().split() if x]
    return int(max(vals)) if vals else 0
d = m["http_req_duration"]
print(f"{variant:<8} {rate:>6} {m['http_reqs']['rate']:>9.1f} {m['http_req_failed']['value']*100:>7.1f}% "
      f"{d['p(50)']:>7.0f}ms {d['p(95)']:>7.0f}ms {peak(pending):>8} {peak(active):>8}")
PY
    rm -f "$pending" "$active"
    sleep 5   # let the pool drain before the next rate
  done
done
