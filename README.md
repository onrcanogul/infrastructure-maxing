# infrastructure-maxing

Learning infrastructure behaviour by *measuring* it, using a payment service as the subject:
idempotency, 12-factor configuration, liveness/readiness, timeouts, and transaction boundaries.

```
services/payment-service   Spring Boot 4 / JDK 25 application (built with mvnw)
load/                      k6 load lab — scenario, raw results, generated HTML report
compose.yaml               postgres + (lab profile) provider-stub + payment-service
```

## Running it

```bash
docker compose up -d postgres
cd services/payment-service && ./mvnw spring-boot:run
```

Needs JDK 25. Maven does not need to be installed — the repo ships the `mvnw` wrapper.

## Load lab

Putting the 200 ms provider call inside the transaction caps throughput at **47.8 RPS**; moving it
out lifts the same code on the same hardware to **595.9 RPS**. Scenario, raw k6 summaries, the
generated report, and how to reproduce any of it: **[`load/`](load/README.md)**.
