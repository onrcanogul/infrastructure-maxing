package com.inframaxing.paymentservice;

import com.inframaxing.paymentservice.dto.PaymentResponse;
import com.inframaxing.paymentservice.exception.PaymentVersionConflictException;
import com.inframaxing.paymentservice.model.Money;
import com.inframaxing.paymentservice.model.Payment;
import com.inframaxing.paymentservice.model.PaymentStatus;
import com.inframaxing.paymentservice.repository.PaymentRepository;
import com.inframaxing.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class PaymentServiceApplicationTests {

	@Autowired
	RestTestClient client;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PaymentService paymentService;

	@Autowired
	PaymentRepository paymentRepository;

	@Test
	void createsPayment() {
		UUID merchantId = UUID.randomUUID();

		PaymentResponse created = post("key-1", request(merchantId, 1250, "TRY", "order-1"))
				.expectStatus().isCreated()
				.expectHeader().exists("Location")
				.expectBody(PaymentResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(created).isNotNull();
		assertThat(created.status()).isEqualTo(PaymentStatus.CREATED);
		assertThat(created.amountMinor()).isEqualTo(1250);
		assertThat(created.currency()).isEqualTo("TRY");

		PaymentResponse fetched = client.get().uri("/v1/payments/{id}", created.id())
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

		UUID first = create("key-2", body, HttpStatus.CREATED);
		UUID second = create("key-2", body, HttpStatus.OK);

		assertThat(second).isEqualTo(first);
		assertThat(paymentCount(merchantId)).isEqualTo(1);
	}

	@Test
	void rejectsSameKeyWithDifferentRequest() {
		UUID merchantId = UUID.randomUUID();
		create("key-3", request(merchantId, 500, "EUR", "order-3"), HttpStatus.CREATED);

		post("key-3", request(merchantId, 999, "EUR", "order-3"))
				.expectStatus().isEqualTo(HttpStatus.CONFLICT);

		assertThat(paymentCount(merchantId)).isEqualTo(1);
	}

	@Test
	void sameKeyFromDifferentMerchantsCreatesSeparatePayments() {
		UUID first = create("shared-key", request(UUID.randomUUID(), 100, "USD", null), HttpStatus.CREATED);
		UUID second = create("shared-key", request(UUID.randomUUID(), 100, "USD", null), HttpStatus.CREATED);

		assertThat(second).isNotEqualTo(first);
	}

	@Test
	void concurrentRequestsWithSameKeyCreateSinglePayment() throws Exception {
		UUID merchantId = UUID.randomUUID();
		Map<String, Object> body = request(merchantId, 700, "TRY", "order-4");
		CountDownLatch start = new CountDownLatch(1);
		List<EntityExchangeResult<PaymentResponse>> results;

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Callable<EntityExchangeResult<PaymentResponse>>> calls = IntStream.range(0, 16)
					.<Callable<EntityExchangeResult<PaymentResponse>>>mapToObj(i -> () -> {
						start.await();
						return post("key-4", body).expectBody(PaymentResponse.class).returnResult();
					})
					.toList();
			List<Future<EntityExchangeResult<PaymentResponse>>> futures = calls.stream().map(executor::submit).toList();
			start.countDown();
			results = futures.stream().map(PaymentServiceApplicationTests::await).toList();
		}

		List<HttpStatusCode> statuses = results.stream().map(EntityExchangeResult::getStatus).toList();
		assertThat(statuses).containsOnly(HttpStatus.CREATED, HttpStatus.OK);
		assertThat(statuses).filteredOn(HttpStatus.CREATED::equals).hasSize(1);
		assertThat(results.stream().map(r -> r.getResponseBody().id()).distinct()).hasSize(1);
		assertThat(paymentCount(merchantId)).isEqualTo(1);
	}

	@Test
	void staleVersionUpdateIsRejected() {
		UUID id = paymentService.create(UUID.randomUUID(), "key-8", new Money(100, "TRY"), null).payment().id();
		Payment stale = paymentService.get(id);

		Payment authorized = paymentService.authorize(id, "acme", "ref-1");
		stale.fail("acme", "timeout");

		assertThat(authorized.version()).isEqualTo(1);
		assertThatThrownBy(() -> paymentRepository.update(stale))
				.isInstanceOf(PaymentVersionConflictException.class);
		assertThat(paymentService.get(id))
				.extracting(Payment::status, Payment::version)
				.containsExactly(PaymentStatus.AUTHORIZED, 1L);
	}

	@Test
	void fullLifecycleIncrementsVersion() {
		UUID id = paymentService.create(UUID.randomUUID(), "key-10", new Money(100, "TRY"), null).payment().id();

		paymentService.authorize(id, "acme", "ref-1");
		Payment captured = paymentService.capture(id);

		assertThat(captured)
				.extracting(Payment::status, Payment::providerRef, Payment::version)
				.containsExactly(PaymentStatus.CAPTURED, "ref-1", 2L);
	}

	@Test
	void concurrentUpdatesOnSameVersionLetExactlyOneWin() throws Exception {
		UUID id = paymentService.create(UUID.randomUUID(), "key-9", new Money(100, "TRY"), null).payment().id();
		List<Payment> copies = IntStream.range(0, 16).mapToObj(i -> paymentService.get(id)).toList();
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Payment>> futures;

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			futures = IntStream.range(0, 16)
					.mapToObj(i -> executor.submit(() -> {
						Payment copy = copies.get(i);
						if (i % 2 == 0) {
							copy.authorize("acme", "ref-" + i);
						} else {
							copy.fail("acme", "declined-" + i);
						}
						start.await();
						return paymentRepository.update(copy);
					}))
					.toList();
			start.countDown();
		}

		long winners = futures.stream().filter(f -> f.state() == Future.State.SUCCESS).count();
		List<Throwable> losers = futures.stream()
				.filter(f -> f.state() == Future.State.FAILED)
				.map(Future::exceptionNow)
				.toList();

		assertThat(winners).isEqualTo(1);
		assertThat(losers).hasSize(15).allMatch(PaymentVersionConflictException.class::isInstance);
		assertThat(paymentService.get(id).version()).isEqualTo(1);
	}

	@Test
	void returnsNotFoundForUnknownPayment() {
		client.get().uri("/v1/payments/{id}", UUID.randomUUID())
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void rejectsMissingIdempotencyKey() {
		client.post().uri("/v1/payments")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request(UUID.randomUUID(), 100, "TRY", null))
				.exchange()
				.expectStatus().isBadRequest();
	}

	@Test
	void rejectsInvalidRequest() {
		post("key-5", request(UUID.randomUUID(), -1, "try", null))
				.expectStatus().isBadRequest();
	}

	@Test
	void rejectsUnknownCurrency() {
		post("key-6", request(UUID.randomUUID(), 100, "XYZ", null))
				.expectStatus().isBadRequest();
	}

	@Test
	void exposesPrometheusMetrics() {
		Map<String, Object> body = request(UUID.randomUUID(), 100, "TRY", null);
		create("key-7", body, HttpStatus.CREATED);
		create("key-7", body, HttpStatus.OK);

		String metrics = client.get().uri("/actuator/prometheus")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class)
				.returnResult()
				.getResponseBody();

		assertThat(metrics)
				.contains("payments_initiated_total")
				.contains("payments_idempotency_total")
				.contains("application=\"payment-service\"");
	}

	private RestTestClient.ResponseSpec post(String key, Map<String, Object> body) {
		return client.post().uri("/v1/payments")
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.exchange();
	}

	private UUID create(String key, Map<String, Object> body, HttpStatus expected) {
		return post(key, body)
				.expectStatus().isEqualTo(expected)
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

	private static <T> T await(Future<T> future) {
		try {
			return future.get();
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}
}
