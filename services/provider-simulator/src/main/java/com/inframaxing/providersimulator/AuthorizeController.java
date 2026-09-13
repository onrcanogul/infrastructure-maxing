package com.inframaxing.providersimulator;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthorizeController {

	private final Cache<String, Authorization> seen;

	private final SimulatorSettings settings;

	public AuthorizeController(SimulatorSettings settings) {
		this.settings = settings;
		this.seen = Caffeine.newBuilder()
				.expireAfterWrite(settings.idempotency().ttl())
				.maximumSize(settings.idempotency().maxEntries())
				.build();
	}

	@PostMapping("/providers/{code}/authorize")
	public Authorization authorize(
			@PathVariable String code,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody(required = false) AuthorizeRequest request) throws InterruptedException {

		Thread.sleep(settings.latency());

		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return decide();
		}
		return seen.get(code + "|" + idempotencyKey, key -> decide());
	}

	private Authorization decide() {
		return new Authorization("sim-" + UUID.randomUUID(),
				settings.approve() ? Outcome.APPROVED : Outcome.DECLINED);
	}

	public record Authorization(String providerRef, Outcome outcome) {
	}

	public enum Outcome {
		APPROVED,
		DECLINED
	}

	public record AuthorizeRequest(UUID paymentId, long amountMinor, String currency) {
	}
}
