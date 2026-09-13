# Load lab — transaction boundary

How much earlier does the system fall over if a network call sits *inside* the database
transaction? Same code, same hardware, same 10-connection pool; the only difference is where the
transaction boundary is drawn around a provider that answers in 200 ms.

| Variant | Ceiling | Why |
|---|---|---|
| `inside` — provider call within the transaction | **47.8 RPS** | Little's law: 10 connections ÷ 206.5 ms hold time = 48.4 RPS |
| `outside` — provider call between two transactions | **595.9 RPS** | 800–1000 is the edge, 1200 collapses on every run |

A connection is checked out the moment the transaction begins and returned only on commit or
rollback. Anything in between — including waiting on someone else's server — holds it. So with the
call inside, your capacity is `pool size ÷ their response time`, a number the other party controls.

Charted report, open in a browser: **[`report.html`](report.html)** — offered vs achieved
throughput, p95 latency, error rate, and all 22 runs as a table.

## Files

```
tx-boundary.js         k6 scenario (constant-arrival-rate, 30s per step)
run-tx-boundary.sh     sweeps request rates, samples Hikari gauges, writes results/
provider-stub/         WireMock mapping — every authorization answers after 200 ms
results/*.json         raw k6 summaries, one per variant/rate
build-report.mjs       results/*.json -> report.html
report.template.html   layout and chart code; data is injected at __DATA_JSON__
report.html            generated — do not edit by hand, re-run the script
```

## Reproducing it

```bash
docker compose --profile lab up -d --build
load/run-tx-boundary.sh "outside:30"                       # warm the JVM — a cold one skews everything
load/run-tx-boundary.sh "inside:20 40 50 60 80" "outside:50 100 400 600 1000"
node load/build-report.mjs                                 # results/*.json -> report.html
docker compose --profile lab down
```

`run-tx-boundary.sh` needs bash, docker, and python3 (it prints a per-run summary line).
`build-report.mjs` needs only Node, and runs on Windows and WSL alike.

Knobs worth turning:

- **Provider latency** — `provider-stub/authorize.json`, `fixedDelayMilliseconds`. Set it to 1000
  and the `inside` ceiling should land near 10 ÷ 1.0 = 10 RPS. Measure and check.
- **Pool size** — `PAYROUTE_DB_POOL_MAX_SIZE` in `compose.yaml`. At 20 the `inside` ceiling should
  roughly double, to ~97 RPS.

## Reading the numbers

- The committed `results/*.json` come from a single sweep. `outside-800` and `outside-1000` show a
  collapse there while an earlier run passed both cleanly — that inconsistency *is* the finding, and
  it is why 600 is quoted as the safe ceiling rather than 1000.
- Above the ceiling the surplus turns into errors at roughly `(offered − ceiling) / offered`.
  Requests that do get through wait out the full Hikari `connection-timeout` (3 s), so p50 pins
  near 3000 ms for `inside`.
- `outside-1200` has a 341 ms p50 next to a 12 s p95 and 27.6% errors: the traffic split into
  requests that sailed through and requests that died in the queue. An average would hide that.
- A pool running near its ceiling is fragile. On a cold JVM even 20 RPS produced 9% errors: a
  queued request still holds its connection for the full 200 ms once it gets one, so the queue does
  not drain itself. The system can stay collapsed under traffic it could normally serve —
  metastable failure.

## Metrics to watch (Prometheus, already exposed)

| Metric | Reads as |
|---|---|
| `hikaricp_connections_pending` | requests waiting for a connection; persistently > 0 is an alarm |
| `hikaricp_connections_active` | pinned to pool size means you are at the ceiling |
| `hikaricp_connections_usage_seconds` | hold time per checkout; a jump from 1 ms to 200 ms means someone put a network call in a transaction |
| `hikaricp_connections_acquire_seconds` | time spent waiting to get a connection |
| `hikaricp_connections_timeout_total` | requests that gave up after `connection-timeout` |

## Environment the numbers came from

Docker Desktop (8 CPU / 8 GB), postgres:17, WireMock provider fixed at 200 ms, payment-service with
a 10-connection pool, Hikari `connection-timeout` 3 s, virtual threads on. k6 drives a constant
arrival rate — requests keep coming whether the server keeps up or not — 30 s per step.
