# Experiment 1 — transaction boundary, on the lab endpoints

| | |
|---|---|
| Endpoint | `POST /lab/tx-boundary/{inside,outside}` (throwaway, `PAYROUTE_LAB_ENABLED=true`) |
| Provider | WireMock stub, fixed **200 ms** |
| Executor | k6 `constant-arrival-rate` — offered rate is forced, the server keeps up or it doesn't |
| Swept | the request rate: `inside-5 … inside-100`, `outside-10 … outside-1500` |
| File name | `<variant>-<offered RPS>.json` |
| Question | at what offered rate does the pool run dry? |

Answer: **inside 47.8 RPS**, **outside 595.9 RPS** clean. Charted: [`../report.html`](../report.html),
written up in [`notes/06`](../../notes/06-transaction-boundary.md) § 3.

These files are the `--summary-export` shape, without a `conditions` block — the conditions are
identical across the sweep and live in the note. Experiment 2's files carry their own conditions
because its variables change per run.

Produced by [`../run-tx-boundary.sh`](../run-tx-boundary.sh) + [`../tx-boundary.js`](../tx-boundary.js).
Read by [`../build-report.mjs`](../build-report.mjs).
