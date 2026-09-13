package com.inframaxing.paymentservice.application;

import com.inframaxing.paymentservice.domain.Money;
import com.inframaxing.paymentservice.infrastructure.JdbcIdempotencyStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

	private final JdbcIdempotencyStore store;
	private final MeterRegistry meterRegistry;

	public IdempotencyService(JdbcIdempotencyStore store, MeterRegistry meterRegistry) {
		this.store = store;
		this.meterRegistry = meterRegistry;
	}

	public String requestHash(UUID merchantId, Money money, String reference) {
		String canonical = String.join("|",
				merchantId.toString(),
				Long.toString(money.amountMinor()),
				money.currency(),
				Objects.toString(reference, ""));
		return HexFormat.of().formatHex(sha256(canonical));
	}

	public Optional<UUID> findPaymentId(UUID merchantId, String key, String requestHash) {
		return store.find(merchantId, key).map(entry -> {
			if (!entry.requestHash().equals(requestHash)) {
				record("key_reused");
				throw new KeyReusedException(key);
			}
			record("replayed");
			return entry.paymentId();
		});
	}

	public boolean claim(UUID merchantId, String key, String requestHash, UUID paymentId) {
		boolean claimed = store.insertIfAbsent(merchantId, key, requestHash, paymentId);
		if (!claimed) {
			record("race_lost");
		}
		return claimed;
	}

	private void record(String outcome) {
		meterRegistry.counter("payments.idempotency", "outcome", outcome).increment();
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public static class KeyReusedException extends RuntimeException {

		public KeyReusedException(String key) {
			super("idempotency key already used with a different request: " + key);
		}
	}
}
