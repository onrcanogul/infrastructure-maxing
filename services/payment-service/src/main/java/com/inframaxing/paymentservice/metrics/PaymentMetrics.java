package com.inframaxing.paymentservice.metrics;

import com.inframaxing.paymentservice.model.Money;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

	private final MeterRegistry registry;
	private final Counter created;
	private final Counter replayed;
	private final Counter conflict;
	private final Counter invalid;

	public PaymentMetrics(MeterRegistry registry) {
		this.registry = registry;
		this.created = requests("created");
		this.replayed = requests("replayed");
		this.conflict = requests("conflict");
		this.invalid = requests("invalid");
	}

	public void created(Money money) {
		created.increment();
		DistributionSummary.builder("payment.created.amount.minor")
				.description("Amount of created payments in minor units")
				.tag("currency", money.currency())
				.register(registry)
				.record(money.amountMinor());
	}

	public void replayed() {
		replayed.increment();
	}

	public void conflict() {
		conflict.increment();
	}

	public void invalid() {
		invalid.increment();
	}

	private Counter requests(String outcome) {
		return Counter.builder("payment.requests")
				.description("Payment creation requests by outcome")
				.tag("outcome", outcome)
				.register(registry);
	}
}
