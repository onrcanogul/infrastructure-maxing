# Load lab — transaction boundary

How much earlier does the system fall over if a network call sits *inside* the database
transaction? A connection is checked out the moment the transaction begins and returned only on
commit. Anything in between — including waiting on someone else's server — holds it. So with the
call inside, your capacity is `pool size ÷ their response time`: a number the other party sets.

That claim was measured **twice**, on purpose. Both sweep an `inside` and an `outside` variant, and
that is exactly why they are easy to confuse — so here is what differs:

| | **Experiment 1** — the lab | **Experiment 2** — the real endpoint |
|---|---|---|
| Results | [`results-tx-boundary-lab/`](results-tx-boundary-lab/) | [`results-create-payment/`](results-create-payment/) |
| Endpoint | `/lab/tx-boundary/{variant}`, built for the experiment | `POST /v1/payments`, the production path |
| Provider | WireMock stub, **200 ms** | `provider-simulator`, **5000 ms** |
| Executor | `constant-arrival-rate` — forced offered rate | `constant-vus` — fixed users, each waits |
| What varies | the offered rate (5 → 1500) | the boundary, and the user count |
| File name | `<variant>-<offered RPS>.json` | `<variant>-<VUs>vu.json` |
| Answers | *where is the ceiling?* | *what does it cost on the real endpoint?* |
| `inside` | **47.8 RPS** | **1.977 RPS**, 46.2% errors |
| `outside` | **595.9 RPS** clean | **19.810 RPS**, no errors |

Experiment 1 finds the ceiling by pushing traffic past it — that is what a forced arrival rate is
for. Experiment 2 does not look for a ceiling; it holds the users constant and moves the boundary,
so the two numbers are a before and an after of the same fix.

Charted report for experiment 1: **[`report.html`](report.html)** — offered vs achieved throughput,
p95 latency, error rate, and all 22 runs as a table. Experiment 2 has three runs and no chart.

Full write-up with the reasoning behind both:
[`notes/06-transaction-boundary.md`](../notes/06-transaction-boundary.md) (§ 3 and § 8).

## Experiment 1 — the lab endpoints

| Variant | Ceiling | Why |
|---|---|---|
| `inside` — provider call within the transaction | **47.8 RPS** | Little's law: 10 connections ÷ 206.5 ms hold time = 48.4 RPS |
| `outside` — provider call between two transactions | **595.9 RPS** | 800–1000 is the edge, 1200 collapses on every run |

## Experiment 2 — the production path

The lab proves the mechanism on throwaway endpoints. This one is the same mistake on the endpoint
customers actually call: `POST /v1/payments`, with the provider call first inside the transaction
and then outside it.

Provider is `provider-simulator` at a 5000 ms profile, pool 10, `PAYROUTE_PROVIDER_READ_TIMEOUT=10s`
so the pool is what gets measured rather than the timeout. k6 `constant-vus`, 30s.

| | Inside, 20 VUs | Outside, 20 VUs | Outside, 100 VUs |
|---|---|---|---|
| Succeeded | 70 — **1.977 RPS** | 120 — **3.984 RPS** | 600 — **19.810 RPS** |
| Failed | **60** (46.2%) | 0 | 0 |
| p95 | 7.04 s | 5.03 s | 5.09 s |

Every one of those 60 failures is the same line: `Connection is not available, request timed out
after 3000ms (total=10, active=10, idle=0, waiting=9)`. Little's law predicted 10 ÷ 5.0 s = 2.0 RPS;
the measurement came in at 1.977.

Afterwards the pool is no longer the limit — hold time per checkout fell from ~5000 ms to 3.60 ms,
and five times the users bought five times the throughput with no errors. Full write-up with the
reasoning: [`notes/06-transaction-boundary.md`](../notes/06-transaction-boundary.md) § 8.

```bash
docker compose up -d postgres
# provider-simulator on 8081, payment-service on 8080 with PAYROUTE_PROVIDER_READ_TIMEOUT=10s
curl -X POST localhost:8081/admin/acme/profile -H 'Content-Type: application/json' \
     -d '{"latencyMs":5000,"errorRate":0,"timeoutRate":0}'

docker run --rm -v "$PWD/load:/load" grafana/k6:1.3.0 run --quiet /load/create-payment.js \
  -e VUS=20 -e DURATION=30s -e OUT=outside-20vu -e VARIANT=outside \
  -e PROVIDER_LATENCY_MS=5000 -e PROVIDER_READ_TIMEOUT=10s -e MEASURED_AT=2026-09-13
```

The script writes `results-create-payment/$OUT.json` itself, with those conditions embedded — pass
them honestly or the file will lie about what produced it.

## Files

Grouped by which experiment owns them — nothing here is shared except the provider stub.

```
experiment 1 — the lab
  tx-boundary.js             k6 scenario, constant-arrival-rate, 30s per step
  run-tx-boundary.sh         sweeps the offered rate, samples Hikari gauges
  provider-stub/             WireMock mapping — every authorization answers after 200 ms
  results-tx-boundary-lab/   raw k6 summaries, one per variant/rate  (+ its own README)
  build-report.mjs           results-tx-boundary-lab/*.json -> report.html
  report.template.html       layout and chart code; data is injected at __DATA_JSON__
  report.html                generated — do not edit by hand, re-run the script

experiment 2 — the production path
  create-payment.js          k6 scenario for POST /v1/payments, constant-vus.
                             Writes its own result file, conditions included.
  results-create-payment/    raw k6 summaries, one per variant/VUs  (+ its own README)
```

## Reproducing it

```bash
docker compose --profile lab up -d --build
load/run-tx-boundary.sh "outside:30"                       # warm the JVM — a cold one skews everything
load/run-tx-boundary.sh "inside:20 40 50 60 80" "outside:50 100 400 600 1000"
node load/build-report.mjs                                 # experiment 1 results -> report.html
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

- The committed `results-tx-boundary-lab/*.json` come from a single sweep. `outside-800` and `outside-1000` show a
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
