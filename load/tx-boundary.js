// k6: a constant arrival rate against one variant of the transaction-boundary lab.
//   VARIANT=inside|outside  RATE=<requests per second>  DURATION=30s  BASE_URL=http://payment-service:8080
// Constant ARRIVAL rate (not a fixed number of users): requests keep coming whether or not
// the server keeps up, which is what real traffic does - and what exposes a pool running dry.
import http from 'k6/http';
import { check } from 'k6';

const VARIANT = __ENV.VARIANT || 'inside';
const RATE = Number(__ENV.RATE || 20);
const BASE_URL = __ENV.BASE_URL || 'http://payment-service:8080';

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    load: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: Math.max(50, RATE * 2),
      maxVUs: Math.max(400, RATE * 8),
    },
  },
};

const body = JSON.stringify({
  merchantId: '3f1c2a4e-8b7d-4c1a-9e2f-1a2b3c4d5e6f',
  amountMinor: 1999,
  currency: 'EUR',
  reference: 'k6',
});

export default function () {
  const res = http.post(`${BASE_URL}/lab/tx-boundary/${VARIANT}`, body, {
    headers: { 'Content-Type': 'application/json' },
    timeout: '15s',
  });
  check(res, { created: (r) => r.status === 201 });
}
