package com.inframaxing.providersimulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {"app.simulator.latency=0ms", "app.simulator.timeout-sleep=600ms"})
@AutoConfigureRestTestClient
class BehaviourProfileTest {

	@Autowired
	private RestTestClient client;

	@Test
	void latency_changes_without_a_restart() {
		String provider = provider();

		authorize(provider).expectStatus().isOk();

		setProfile(provider, 300, 0d, 0d);

		long started = System.nanoTime();
		authorize(provider).expectStatus().isOk();
		long elapsedMs = (System.nanoTime() - started) / 1_000_000;

		assertThat(elapsedMs).isGreaterThanOrEqualTo(300);
	}

	@Test
	void a_full_error_rate_answers_500() {
		String provider = provider();

		setProfile(provider, 0, 1d, 0d);

		authorize(provider).expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Test
	void a_full_timeout_rate_never_answers_in_time() {
		String provider = provider();

		setProfile(provider, 0, 0d, 1d);

		long started = System.nanoTime();
		authorize(provider);
		long elapsedMs = (System.nanoTime() - started) / 1_000_000;

		assertThat(elapsedMs).isGreaterThanOrEqualTo(600);
	}

	@Test
	void a_profile_belongs_to_one_provider_only() {
		String slow = provider();
		String untouched = provider();

		setProfile(slow, 0, 1d, 0d);

		authorize(slow).expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		authorize(untouched).expectStatus().isOk();
	}

	@Test
	void the_profile_is_readable_and_echoed_back() {
		String provider = provider();

		BehaviourProfile written = setProfile(provider, 120, 0.25d, 0.1d);

		BehaviourProfile read = client.get().uri("/admin/{code}/profile", provider)
				.exchange()
				.expectStatus().isOk()
				.expectBody(BehaviourProfile.class)
				.returnResult()
				.getResponseBody();

		assertThat(written).isEqualTo(new BehaviourProfile(120, 0.25d, 0.1d));
		assertThat(read).isEqualTo(written);
	}

	@Test
	void an_impossible_profile_is_rejected() {
		client.post().uri("/admin/{code}/profile", provider())
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("latencyMs", 0, "errorRate", 0.8d, "timeoutRate", 0.8d))
				.exchange()
				.expectStatus().isBadRequest();
	}

	private BehaviourProfile setProfile(String provider, long latencyMs, double errorRate, double timeoutRate) {
		return client.post().uri("/admin/{code}/profile", provider)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("latencyMs", latencyMs, "errorRate", errorRate, "timeoutRate", timeoutRate))
				.exchange()
				.expectStatus().isOk()
				.expectBody(BehaviourProfile.class)
				.returnResult()
				.getResponseBody();
	}

	private RestTestClient.ResponseSpec authorize(String provider) {
		return client.post().uri("/providers/{code}/authorize", provider)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of(
						"paymentId", UUID.randomUUID(),
						"amountMinor", 1999,
						"currency", "EUR"))
				.exchange();
	}

	private static String provider() {
		return "p-" + UUID.randomUUID();
	}
}
