# Experiment 2 — transaction boundary, on the real endpoint

| | |
|---|---|
| Endpoint | `POST /v1/payments` (the production path) |
| Provider | `provider-simulator`, profile set to **5000 ms** |
| Executor | k6 `constant-vus` — a fixed number of users, each waiting for its answer |
| Varied | the boundary (inside → outside) and the user count |
| File name | `<variant>-<VUs>vu.json` |
| Question | what does the same mistake cost on the endpoint customers actually call? |

Answer: **inside 1.977 RPS with 46.2% errors**, **outside 19.810 RPS with none**. Written up in
[`notes/06`](../../notes/06-transaction-boundary.md) § 8.

Every file carries a `conditions` block — provider latency, pool size, read timeout, VUs — because
those are the variables here. A rate without them is not a measurement.

Produced by [`../create-payment.js`](../create-payment.js), which writes the file itself via
`handleSummary`. Not read by `build-report.mjs`; that generator belongs to experiment 1.
