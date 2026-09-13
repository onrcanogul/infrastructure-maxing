package com.inframaxing.providersimulator;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.simulator")
public record SimulatorSettings(Duration latency, boolean approve, Idempotency idempotency) {

	public record Idempotency(Duration ttl, long maxEntries) {
	}
}
