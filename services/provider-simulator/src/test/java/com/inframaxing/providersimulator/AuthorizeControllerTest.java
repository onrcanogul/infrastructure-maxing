package com.inframaxing.providersimulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.inframaxing.providersimulator.AuthorizeController.Authorization;
import com.inframaxing.providersimulator.AuthorizeController.Outcome;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "app.simulator.latency=20ms")
@AutoConfigureRestTestClient
class AuthorizeControllerTest {

	@Autowired
	private RestTestClient client;

	@Test
	void approves_and_waits_the_configured_latency() {
		long started = System.nanoTime();

		Authorization authorization = authorize("acme", UUID.randomUUID().toString());

		long elapsedMs = (System.nanoTime() - started) / 1_000_000;

		assertThat(authorization.outcome()).isEqualTo(Outcome.APPROVED);
		assertThat(authorization.providerRef()).startsWith("sim-");
		assertThat(elapsedMs).isGreaterThanOrEqualTo(20);
	}

	@Test
	void same_key_returns_the_same_provider_ref() {
		String key = UUID.randomUUID().toString();

		Authorization first = authorize("acme", key);
		Authorization second = authorize("acme", key);

		assertThat(second.providerRef()).isEqualTo(first.providerRef());
		assertThat(second.outcome()).isEqualTo(first.outcome());
	}

	@Test
	void a_different_key_is_a_different_authorization() {
		Authorization first = authorize("acme", UUID.randomUUID().toString());
		Authorization second = authorize("acme", UUID.randomUUID().toString());

		assertThat(second.providerRef()).isNotEqualTo(first.providerRef());
	}

	@Test
	void the_same_key_at_another_provider_is_a_different_authorization() {
		String key = UUID.randomUUID().toString();

		Authorization acme = authorize("acme", key);
		Authorization globex = authorize("globex", key);

		assertThat(globex.providerRef()).isNotEqualTo(acme.providerRef());
	}

	@Test
	void without_a_key_every_call_is_a_new_authorization() {
		Authorization first = authorize("acme", null);
		Authorization second = authorize("acme", null);

		assertThat(second.providerRef()).isNotEqualTo(first.providerRef());
	}

	private Authorization authorize(String providerCode, String idempotencyKey) {
		RestTestClient.RequestBodySpec request = client.post()
				.uri("/providers/{code}/authorize", providerCode)
				.contentType(MediaType.APPLICATION_JSON);

		if (idempotencyKey != null) {
			request = request.header("Idempotency-Key", idempotencyKey);
		}

		return request
				.body(Map.of(
						"paymentId", UUID.randomUUID(),
						"amountMinor", 1999,
						"currency", "EUR"))
				.exchange()
				.expectStatus().isOk()
				.expectBody(Authorization.class)
				.returnResult()
				.getResponseBody();
	}
}
