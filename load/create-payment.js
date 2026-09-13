// k6: N concurrent users creating payments, each with its own idempotency key.
//   VUS=20 DURATION=30s VARIANT=inside PROVIDER_LATENCY_MS=5000 OUT=inside-20vu
// Constant VUs, not a constant arrival rate: every user waits for its answer before asking
// again, so the achieved rate is exactly what the server managed to finish.
//
// The run conditions are written into the result file. A number without the latency, the pool
// size and the boundary it was measured at is not a measurement.
import http from 'k6/http';
import { Counter } from 'k6/metrics';

const VUS = Number(__ENV.VUS || 20);
const DURATION = __ENV.DURATION || '30s';
const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const OUT = __ENV.OUT || 'run';
// Two runs of the same OUT would otherwise send identical keys, and every request after the
// first would be a replay - a full run of 200s that measure nothing.
const RUN_ID = `${OUT}-${Date.now()}`;

export const options = {
  discardResponseBodies: false,
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    load: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
};

const authorized = new Counter('authorized');
const poolExhausted = new Counter('pool_exhausted');
const providerTimeout = new Counter('provider_timeout');
const otherError = new Counter('other_error');

const body = JSON.stringify({
  merchantId: '3f1c2a4e-8b7d-4c1a-9e2f-1a2b3c4d5e6f',
  amountMinor: 1999,
  currency: 'EUR',
  reference: 'k6-create',
});

export default function () {
  const res = http.post(`${BASE_URL}/v1/payments`, body, {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': `k6-${RUN_ID}-${__VU}-${__ITER}`,
    },
    timeout: '30s',
  });

  if (res.status === 201) {
    authorized.add(1);
  } else if (res.status === 504) {
    providerTimeout.add(1);
  } else if (res.status === 500 && String(res.body).includes('Connection is not available')) {
    poolExhausted.add(1);
  } else {
    otherError.add(1);
  }
}

export function handleSummary(data) {
  const conditions = {
    variant: __ENV.VARIANT || 'unknown',
    vus: VUS,
    note: __ENV.NOTE || '',
    endpoint: 'POST /v1/payments',
    provider: __ENV.PROVIDER || 'provider-simulator',
    providerLatencyMs: Number(__ENV.PROVIDER_LATENCY_MS || 0),
    providerReadTimeout: __ENV.PROVIDER_READ_TIMEOUT || 'unknown',
    poolMaxSize: Number(__ENV.POOL_MAX_SIZE || 10),
    hikariConnectionTimeoutMs: Number(__ENV.HIKARI_CONNECTION_TIMEOUT_MS || 3000),
    executor: 'constant-vus',
    duration: DURATION,
    measuredAt: __ENV.MEASURED_AT || '',
  };

  const rate = (name) => (data.metrics[name] ? data.metrics[name].values.rate : 0);
  const count = (name) => (data.metrics[name] ? data.metrics[name].values.count : 0);
  const line =
    `${conditions.variant} ${VUS}VU @${conditions.providerLatencyMs}ms -> ` +
    `${rate('authorized').toFixed(3)} RPS ok, ` +
    `${count('pool_exhausted')} pool, ${count('provider_timeout')} timeout, ` +
    `${count('other_error')} other\n`;

  // Flattened the same way --summary-export writes it, so every file in the directory reads alike.
  const metrics = {};
  for (const [name, metric] of Object.entries(data.metrics)) {
    metrics[name] = metric.values;
  }

  return {
    stdout: line,
    [`/load/results-create-payment/${OUT}.json`]: JSON.stringify({ conditions, metrics }, null, 2) + '\n',
  };
}
