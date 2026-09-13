package com.inframaxing.paymentservice.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class ProviderMetrics {

	public static final String NAME = "provider.call.duration";

	private final MeterRegistry registry;

	public ProviderMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	public void call(String provider, Outcome outcome, Duration elapsed) {
		Timer.builder(NAME)
				.description("Time spent on one authorization call to a payment provider")
				.tag("provider", provider)
				.tag("outcome", outcome.tag())
				.register(registry)
				.record(elapsed);
	}

	public enum Outcome {
		APPROVED,
		DECLINED,
		ERROR,
		TIMEOUT;

		String tag() {
			return name().toLowerCase();
		}
	}
}
