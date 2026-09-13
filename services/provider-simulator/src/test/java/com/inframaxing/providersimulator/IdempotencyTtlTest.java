package com.inframaxing.providersimulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.inframaxing.providersimulator.AuthorizeController.Authorization;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {"app.simulator.latency=0ms", "app.simulator.idempotency.ttl=150ms"})
@AutoConfigureRestTestClient
class IdempotencyTtlTest {

	@Autowired
	private RestTestClient client;

	@Test
	void a_key_stops_being_remembered_once_its_ttl_passes() throws InterruptedException {
		String key = UUID.randomUUID().toString();

		Authorization first = authorize(key);
		Authorization withinTtl = authorize(key);

		Thread.sleep(400);

		Authorization afterTtl = authorize(key);

		assertThat(withinTtl.providerRef()).isEqualTo(first.providerRef());
		assertThat(afterTtl.providerRef()).isNotEqualTo(first.providerRef());
	}

	private Authorization authorize(String idempotencyKey) {
		return client.post()
				.uri("/providers/{code}/authorize", "acme")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
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
