package com.inframaxing.paymentservice;

import com.inframaxing.paymentservice.dto.PaymentResponse;
import com.inframaxing.paymentservice.model.PaymentStatus;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentIdempotencyIT extends IntegrationTestSupport {

	@Test
	void createsPayment() {
		UUID merchantId = UUID.randomUUID();

		PaymentResponse created = postPayment("create", paymentRequest(merchantId, 1250, "TRY", "order-1"))
				.expectStatus().isCreated()
				.expectHeader().exists("Location")
				.expectBody(PaymentResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(created).isNotNull();
		assertThat(created.status()).isEqualTo(PaymentStatus.AUTHORIZED);
		assertThat(created.amountMinor()).isEqualTo(1250);
		assertThat(created.currency()).isEqualTo("TRY");
		assertThat(paymentCount(merchantId)).isEqualTo(1);
		assertThat(idempotencyKeyCount(merchantId)).isEqualTo(1);

		client.get().uri("/v1/payments/{id}", created.id())
				.exchange()
				.expectStatus().isOk()
				.expectBody(PaymentResponse.class)
				.isEqualTo(created);
	}

	@Test
	void replaysSameRequestWithSameKey() {
		UUID merchantId = UUID.randomUUID();
		Map<String, Object> body = paymentRequest(merchantId, 500, "EUR", "order-2");

		PaymentResponse first = postPayment("replay", body)
				.expectStatus().isCreated()
				.expectBody(PaymentResponse.class)
				.returnResult()
				.getResponseBody();
		PaymentResponse second = postPayment("replay", body)
				.expectStatus().isOk()
				.expectBody(PaymentResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(second).isEqualTo(first);
		assertThat(paymentCount(merchantId)).isEqualTo(1);
	}

	@Test
	void rejectsSameKeyWithDifferentRequest() {
		UUID merchantId = UUID.randomUUID();
		postPayment("conflict", paymentRequest(merchantId, 500, "EUR", "order-3"))
				.expectStatus().isCreated();

		postPayment("conflict", paymentRequest(merchantId, 999, "EUR", "order-3"))
				.expectStatus().isEqualTo(HttpStatus.CONFLICT)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.title").isEqualTo("Idempotency key conflict");

		assertThat(paymentCount(merchantId)).isEqualTo(1);
		assertThat(jdbc.sql("select amount_minor from payment where merchant_id = :merchantId")
				.param("merchantId", merchantId)
				.query(Long.class)
				.single()).isEqualTo(500L);
	}

	@Test
	void rejectsInvalidRequestWithoutWriting() {
		UUID merchantId = UUID.randomUUID();

		postPayment("invalid", paymentRequest(merchantId, -1, "try", null))
				.expectStatus().isBadRequest()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);

		assertThat(paymentCount(merchantId)).isZero();
		assertThat(idempotencyKeyCount(merchantId)).isZero();
	}

	@RepeatedTest(50)
	void concurrentRequestsWithSameKeyCreateExactlyOneRow() throws Exception {
		UUID merchantId = UUID.randomUUID();
		Map<String, Object> body = paymentRequest(merchantId, 700, "TRY", "order-4");
		CyclicBarrier barrier = new CyclicBarrier(2);
		Callable<EntityExchangeResult<PaymentResponse>> request = () -> {
			barrier.await();
			return postPayment("concurrent", body).expectBody(PaymentResponse.class).returnResult();
		};
		List<EntityExchangeResult<PaymentResponse>> results;

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Future<EntityExchangeResult<PaymentResponse>> first = executor.submit(request);
			Future<EntityExchangeResult<PaymentResponse>> second = executor.submit(request);
			results = List.of(first.get(), second.get());
		}

		assertThat(results).extracting(EntityExchangeResult::getStatus)
				.containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.OK);
		assertThat(results.get(1).getResponseBody().id()).isEqualTo(results.get(0).getResponseBody().id());
		assertThat(paymentCount(merchantId)).isEqualTo(1);
		assertThat(idempotencyKeyCount(merchantId)).isEqualTo(1);
	}
}
