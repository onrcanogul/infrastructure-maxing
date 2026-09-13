package com.inframaxing.paymentservice;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMetricsIT extends IntegrationTestSupport {

	@Autowired
	MeterRegistry registry;

	@Test
	void countsEachOutcomeOnce() {
		UUID merchantId = UUID.randomUUID();
		Map<String, Object> body = paymentRequest(merchantId, 1500, "EUR", "order-m");
		double created = requests("created");
		double replayed = requests("replayed");
		double conflict = requests("conflict");
		double invalid = requests("invalid");

		postPayment("metrics-outcomes", body).expectStatus().isCreated();
		postPayment("metrics-outcomes", body).expectStatus().isOk();
		postPayment("metrics-outcomes", paymentRequest(merchantId, 999, "EUR", "order-m"))
				.expectStatus().isEqualTo(HttpStatus.CONFLICT);
		postPayment("metrics-invalid", paymentRequest(merchantId, -1, "EUR", null))
				.expectStatus().isBadRequest();

		assertThat(requests("created") - created).isEqualTo(1);
		assertThat(requests("replayed") - replayed).isEqualTo(1);
		assertThat(requests("conflict") - conflict).isEqualTo(1);
		assertThat(requests("invalid") - invalid).isEqualTo(1);
	}

	@Test
	void countsInvalidForEveryKindOfBadCreateRequest() {
		double invalid = requests("invalid");

		postPayment("metrics-unknown-currency", paymentRequest(UUID.randomUUID(), 100, "XYZ", null))
				.expectStatus().isBadRequest();
		client.post().uri("/v1/payments")
				.contentType(MediaType.APPLICATION_JSON)
				.body(paymentRequest(UUID.randomUUID(), 100, "TRY", null))
				.exchange()
				.expectStatus().isBadRequest();
		client.post().uri("/v1/payments")
				.header("Idempotency-Key", "metrics-bad-json")
				.contentType(MediaType.APPLICATION_JSON)
				.body("{not json")
				.exchange()
				.expectStatus().isBadRequest();

		assertThat(requests("invalid") - invalid).isEqualTo(3);
	}

	@Test
	void doesNotCountInvalidForOtherEndpoints() {
		double invalid = requests("invalid");

		client.get().uri("/v1/payments/not-a-uuid").exchange().expectStatus().isBadRequest();

		assertThat(requests("invalid") - invalid).isZero();
	}

	@Test
	void sumsCreatedAmountPerCurrency() {
		double before = createdAmount("JPY");

		postPayment("metrics-amount-1", paymentRequest(UUID.randomUUID(), 1200, "JPY", null)).expectStatus().isCreated();
		Map<String, Object> replay = paymentRequest(UUID.randomUUID(), 800, "JPY", null);
		postPayment("metrics-amount-2", replay).expectStatus().isCreated();
		postPayment("metrics-amount-2", replay).expectStatus().isOk();

		assertThat(createdAmount("JPY") - before).isEqualTo(2000);
	}

	private double requests(String outcome) {
		return registry.get("payment.requests").tag("outcome", outcome).counter().count();
	}

	private double createdAmount(String currency) {
		DistributionSummary summary = registry.find("payment.created.amount.minor").tag("currency", currency).summary();
		return summary == null ? 0 : summary.totalAmount();
	}
}
