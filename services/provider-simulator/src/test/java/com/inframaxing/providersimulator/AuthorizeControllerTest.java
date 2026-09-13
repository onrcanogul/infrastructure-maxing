package com.inframaxing.providersimulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.inframaxing.providersimulator.AuthorizeController.Decision;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "app.simulator.latency=50ms")
@AutoConfigureRestTestClient
class AuthorizeControllerTest {

	@Autowired
	private RestTestClient client;

	@Test
	void approves_and_waits_the_configured_latency() {
		long started = System.nanoTime();

		Decision decision = client.post().uri("/authorize")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of(
						"paymentId", UUID.randomUUID(),
						"amountMinor", 1999,
						"currency", "EUR"))
				.exchange()
				.expectStatus().isOk()
				.expectBody(Decision.class)
				.returnResult()
				.getResponseBody();

		long elapsedMs = (System.nanoTime() - started) / 1_000_000;

		assertThat(decision).isNotNull();
		assertThat(decision.approved()).isTrue();
		assertThat(decision.code()).isEqualTo("00");
		assertThat(decision.reference()).startsWith("sim-");
		assertThat(elapsedMs).isGreaterThanOrEqualTo(50);
	}
}
