// Turns the k6 summaries in load/results-tx-boundary-lab/ into a single self-contained load/report.html.
// Run it after run-tx-boundary.sh; report.template.html holds the layout, this holds the data.
//
//   node load/build-report.mjs
import { readFileSync, writeFileSync, readdirSync } from "node:fs";
import { dirname, join, basename } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const RESULTS = join(HERE, "results-tx-boundary-lab");
const TEMPLATE = join(HERE, "report.template.html");
const OUT = join(HERE, "report.html");

// Rows worth pointing at in the table's last column, keyed by "<variant>-<rate>".
const NOTES = {
  "inside-45": "pool full, nothing queued",
  "inside-50": "ceiling",
  "outside-50": "inside had drained the pool here",
  "outside-600": "safe ceiling",
};

const VARIANT_ORDER = { inside: 0, outside: 1 };

function row(file) {
  const stem = basename(file, ".json");
  const cut = stem.lastIndexOf("-");
  const variant = stem.slice(0, cut);
  const rate = Number(stem.slice(cut + 1));
  const m = JSON.parse(readFileSync(join(RESULTS, file), "utf8")).metrics;
  const d = m.http_req_duration;
  return {
    v: variant,
    rate,
    ach: Math.round(m.http_reqs.rate * 10) / 10,
    p50: Math.round(d["p(50)"]),
    p95: Math.round(d["p(95)"]),
    p99: Math.round(d["p(99)"]),
    max: Math.round(d.max),
    err: Math.round(m.http_req_failed.value * 10000) / 100,
    reqs: m.http_reqs.count,
    vus: m.vus.max,
    note: NOTES[stem] ?? "",
  };
}

const files = readdirSync(RESULTS).filter(f => f.endsWith(".json"));
if (files.length === 0) {
  console.error(`no k6 summaries in ${RESULTS} — run load/run-tx-boundary.sh first`);
  process.exit(1);
}

const rows = files.map(row).sort((a, b) =>
  (VARIANT_ORDER[a.v] ?? 99) - (VARIANT_ORDER[b.v] ?? 99) ||
  a.v.localeCompare(b.v) ||
  a.rate - b.rate
);

const template = readFileSync(TEMPLATE, "utf8");
writeFileSync(OUT, template.replace("__DATA_JSON__", JSON.stringify(rows, null, 2)), "utf8");

const variants = [...new Set(rows.map(r => r.v))].join(", ");
console.log(`load/report.html <- ${rows.length} runs (${variants})`);
