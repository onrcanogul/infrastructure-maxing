package com.inframaxing.paymentservice;

import com.inframaxing.paymentservice.dto.PaymentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentApiIT extends IntegrationTestSupport {

	@Test
	void sameKeyFromDifferentMerchantsCreatesSeparatePayments() {
		UUID first = createdId(postPayment("shared-key", paymentRequest(UUID.randomUUID(), 100, "USD", null)));
		UUID second = createdId(postPayment("shared-key", paymentRequest(UUID.randomUUID(), 100, "USD", null)));

		assertThat(second).isNotEqualTo(first);
	}

	@Test
	void returnsNotFoundForUnknownPayment() {
		client.get().uri("/v1/payments/{id}", UUID.randomUUID())
				.exchange()
				.expectStatus().isNotFound()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void rejectsMissingIdempotencyKey() {
		client.post().uri("/v1/payments")
				.contentType(MediaType.APPLICATION_JSON)
				.body(paymentRequest(UUID.randomUUID(), 100, "TRY", null))
				.exchange()
				.expectStatus().isBadRequest();
	}

	@Test
	void rejectsUnknownCurrency() {
		postPayment("unknown-currency", paymentRequest(UUID.randomUUID(), 100, "XYZ", null))
				.expectStatus().isBadRequest();
	}

	@Test
	void exposesPrometheusMetrics() {
		Map<String, Object> body = paymentRequest(UUID.randomUUID(), 100, "TRY", null);
		postPayment("metrics", body).expectStatus().isCreated();
		postPayment("metrics", body).expectStatus().isOk();

		String metrics = client.get().uri("/actuator/prometheus")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class)
				.returnResult()
				.getResponseBody();

		assertThat(metrics)
				.contains("payment_requests_total")
				.contains("payment_created_amount_minor_sum")
				.contains("application=\"payment-service\"");
	}

	private UUID createdId(RestTestClient.ResponseSpec response) {
		return response.expectStatus().isCreated()
				.expectBody(PaymentResponse.class)
				.returnResult()
				.getResponseBody()
				.id();
	}
}
