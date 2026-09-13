package com.inframaxing.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class ProviderMetricsIT extends IntegrationTestSupport {

	@Autowired
	MeterRegistry registry;

	@Test
	void anApprovedCallIsTimedUnderItsOwnOutcome() {
		long approved = calls("approved");
		long declined = calls("declined");

		postPayment("metrics-approved-" + UUID.randomUUID(),
				paymentRequest(UUID.randomUUID(), 1999, "EUR", null)).expectStatus().isCreated();

		assertThat(calls("approved") - approved).isEqualTo(1);
		assertThat(calls("declined") - declined).isZero();
		assertThat(totalTime("approved")).isPositive();
	}

	@Test
	void aTimeoutIsTimedSeparatelyFromAnError() {
		long timedOut = calls("timeout");
		long errored = calls("error");
		ProviderStub.answerOnlyWhenReleased();

		postPayment("metrics-timeout-" + UUID.randomUUID(),
				paymentRequest(UUID.randomUUID(), 1999, "EUR", null))
				.expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

		assertThat(calls("timeout") - timedOut).isEqualTo(1);
		assertThat(calls("error") - errored).isZero();

		ProviderStub.release();
	}

	@Test
	void theProviderIsATagAndThePaymentIdIsNotAnywhere() {
		postPayment("metrics-tags-" + UUID.randomUUID(),
				paymentRequest(UUID.randomUUID(), 1999, "EUR", null)).expectStatus().isCreated();

		Timer timer = registry.get("provider.call.duration").tag("outcome", "approved").timer();

		assertThat(timer.getId().getTag("provider")).isEqualTo("acme");
		assertThat(timer.getId().getTags()).extracting("key").contains("provider", "outcome");
		assertThat(timer.getId().getTags())
				.as("no tag may carry an id - that is unbounded cardinality")
				.noneMatch(tag -> tag.getValue().matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"));
	}

	private long calls(String outcome) {
		Timer timer = registry.find("provider.call.duration").tag("outcome", outcome).timer();
		return timer == null ? 0 : timer.count();
	}

	private double totalTime(String outcome) {
		Timer timer = registry.find("provider.call.duration").tag("outcome", outcome).timer();
		return timer == null ? 0 : timer.totalTime(java.util.concurrent.TimeUnit.SECONDS);
	}
}
