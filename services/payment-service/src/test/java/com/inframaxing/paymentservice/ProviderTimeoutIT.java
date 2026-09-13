package com.inframaxing.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.inframaxing.paymentservice.model.PaymentStatus;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.provider.read-timeout=300ms")
class ProviderTimeoutIT extends IntegrationTestSupport {

	@Test
	void a_provider_that_never_answers_gives_up_at_the_read_timeout() throws Exception {
		ProviderStub.answerOnlyWhenReleased();
		UUID merchantId = UUID.randomUUID();

		long started = System.nanoTime();
		postPayment("timeout", paymentRequest(merchantId, 1999, "EUR", "slow-provider"))
				.expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
		long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

		assertThat(elapsedMs).isLessThan(3_000);
		assertThat(paymentCount(merchantId)).isEqualTo(1);
		assertThat(idempotencyKeyCount(merchantId)).isEqualTo(1);
		assertThat(statusOf(merchantId)).isEqualTo(PaymentStatus.TIMEOUT.name());
		assertThat(failureReasonOf(merchantId)).isEqualTo("provider did not answer in time");

		ProviderStub.release();
	}

	private String statusOf(UUID merchantId) {
		return column(merchantId, "status");
	}

	private String failureReasonOf(UUID merchantId) {
		return column(merchantId, "failure_reason");
	}

	private String column(UUID merchantId, String name) {
		return jdbc.sql("select " + name + " from payment where merchant_id = :merchantId")
				.param("merchantId", merchantId)
				.query(String.class)
				.single();
	}
}
