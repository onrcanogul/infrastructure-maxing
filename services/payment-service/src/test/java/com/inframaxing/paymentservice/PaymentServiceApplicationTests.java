package com.inframaxing.paymentservice;

import com.inframaxing.paymentservice.api.PaymentResponse;
import com.inframaxing.paymentservice.domain.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class PaymentServiceApplicationTests {

	@Autowired
	RestTestClient client;

	@Autowired
	JdbcClient jdbc;

	@Test
	void createsPendingPayment() {
		UUID merchantId = UUID.randomUUID();

		PaymentResponse created = client.post().uri("/payments")
				.header("Idempotency-Key", "key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request(merchantId, 1250, "TRY", "order-1"))
				.exchange()
				.expectStatus().isCreated()
				.expectHeader().valueEquals("Idempotent-Replayed", "false")
				.expectBody(PaymentResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(created).isNotNull();
		assertThat(created.status()).isEqualTo(PaymentStatus.PENDING);
		assertThat(created.amountMinor()).isEqualTo(1250);
		assertThat(created.currency()).isEqualTo("TRY");

		PaymentResponse fetched = client.get().uri("/payments/{id}", created.id())
				.exchange()
				.expectStatus().isOk()
				.expectBody(PaymentResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(fetched).isEqualTo(created);
	}

	@Test
	void replaysSameRequestWithSameKey() {
		UUID merchantId = UUID.randomUUID();
		Map<String, Object> body = request(merchantId, 500, "EUR", "order-2");

		UUID first = create("key-2", body, "false");
		UUID second = create("key-2", body, "true");

		assertThat(second).isEqualTo(first);
		assertThat(paymentCount(merchantId)).isEqualTo(1);
	}

	@Test
	void rejectsSameKeyWithDifferentRequest() {
		UUID merchantId = UUID.randomUUID();
		create("key-3", request(merchantId, 500, "EUR", "order-3"), "false");

		client.post().uri("/payments")
				.header("Idempotency-Key", "key-3")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request(merchantId, 999, "EUR", "order-3"))
				.exchange()
				.expectStatus().isEqualTo(422);

		assertThat(paymentCount(merchantId)).isEqualTo(1);
	}

	@Test
	void sameKeyFromDifferentMerchantsCreatesSeparatePayments() {
		UUID first = create("shared-key", request(UUID.randomUUID(), 100, "USD", null), "false");
		UUID second = create("shared-key", request(UUID.randomUUID(), 100, "USD", null), "false");

		assertThat(second).isNotEqualTo(first);
	}

	@Test
	void concurrentRequestsWithSameKeyCreateSinglePayment() throws Exception {
		UUID merchantId = UUID.randomUUID();
		Map<String, Object> body = request(merchantId, 700, "TRY", "order-4");
		CountDownLatch start = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Callable<UUID>> calls = IntStream.range(0, 16)
					.<Callable<UUID>>mapToObj(i -> () -> {
						start.await();
						return client.post().uri("/payments")
								.header("Idempotency-Key", "key-4")
								.contentType(MediaType.APPLICATION_JSON)
								.body(body)
								.exchange()
								.expectStatus().isCreated()
								.expectBody(PaymentResponse.class)
								.returnResult()
								.getResponseBody()
								.id();
					})
					.toList();
			List<Future<UUID>> futures = calls.stream().map(executor::submit).toList();
			start.countDown();

			List<UUID> ids = futures.stream().map(PaymentServiceApplicationTests::await).distinct().toList();
			assertThat(ids).hasSize(1);
		}

		assertThat(paymentCount(merchantId)).isEqualTo(1);
	}

	@Test
	void returnsNotFoundForUnknownPayment() {
		client.get().uri("/payments/{id}", UUID.randomUUID())
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void rejectsMissingIdempotencyKey() {
		client.post().uri("/payments")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request(UUID.randomUUID(), 100, "TRY", null))
				.exchange()
				.expectStatus().isBadRequest();
	}

	@Test
	void rejectsInvalidRequest() {
		client.post().uri("/payments")
				.header("Idempotency-Key", "key-5")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request(UUID.randomUUID(), -1, "try", null))
				.exchange()
				.expectStatus().isBadRequest();
	}

	@Test
	void rejectsUnknownCurrency() {
		client.post().uri("/payments")
				.header("Idempotency-Key", "key-6")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request(UUID.randomUUID(), 100, "XYZ", null))
				.exchange()
				.expectStatus().isBadRequest();
	}

	@Test
	void exposesPrometheusMetrics() {
		create("key-7", request(UUID.randomUUID(), 100, "TRY", null), "false");

		String metrics = client.get().uri("/actuator/prometheus")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class)
				.returnResult()
				.getResponseBody();

		assertThat(metrics).contains("payments_initiated_total").contains("application=\"payment-service\"");
	}

	private UUID create(String key, Map<String, Object> body, String replayed) {
		return client.post().uri("/payments")
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.exchange()
				.expectStatus().isCreated()
				.expectHeader().valueEquals("Idempotent-Replayed", replayed)
				.expectBody(PaymentResponse.class)
				.returnResult()
				.getResponseBody()
				.id();
	}

	private int paymentCount(UUID merchantId) {
		return jdbc.sql("select count(*) from payment where merchant_id = :merchantId")
				.param("merchantId", merchantId)
				.query(Integer.class)
				.single();
	}

	private static Map<String, Object> request(UUID merchantId, long amountMinor, String currency, String reference) {
		Map<String, Object> body = new HashMap<>();
		body.put("merchantId", merchantId);
		body.put("amountMinor", amountMinor);
		body.put("currency", currency);
		body.put("reference", reference);
		return body;
	}

	private static UUID await(Future<UUID> future) {
		try {
			return future.get();
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}
}
