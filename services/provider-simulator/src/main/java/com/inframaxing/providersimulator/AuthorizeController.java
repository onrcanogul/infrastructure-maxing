package com.inframaxing.providersimulator;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthorizeController {

	private final Cache<String, Authorization> seen;

	private final SimulatorSettings settings;

	private final ProfileStore profiles;

	public AuthorizeController(SimulatorSettings settings, ProfileStore profiles) {
		this.settings = settings;
		this.profiles = profiles;
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

		BehaviourProfile profile = profiles.of(code);
		double roll = ThreadLocalRandom.current().nextDouble();

		Thread.sleep(profile.latencyMs());

		if (roll < profile.timeoutRate()) {
			Authorization authorization = remember(code, idempotencyKey);
			Thread.sleep(settings.timeoutSleep());
			return authorization;
		}

		if (roll < profile.timeoutRate() + profile.errorRate()) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "provider unavailable");
		}

		return remember(code, idempotencyKey);
	}

	private Authorization remember(String code, String idempotencyKey) {
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
