package com.inframaxing.paymentservice.service;

import com.inframaxing.paymentservice.exception.IdempotencyKeyConflictException;
import com.inframaxing.paymentservice.model.IdempotencyRecord;
import com.inframaxing.paymentservice.model.Money;
import com.inframaxing.paymentservice.repository.JdbcIdempotencyStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
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

	public void register(UUID merchantId, String key, String requestHash, UUID paymentId) {
		store.insert(merchantId, key, requestHash, paymentId);
	}

	public UUID replay(UUID merchantId, String key, String requestHash) {
		IdempotencyRecord record = store.find(merchantId, key)
				.orElseThrow(() -> new IllegalStateException("idempotency key missing after unique violation: " + key));
		if (!record.requestHash().equals(requestHash)) {
			count("conflict");
			throw new IdempotencyKeyConflictException(key);
		}
		count("replayed");
		return record.paymentId();
	}

	private void count(String outcome) {
		meterRegistry.counter("payments.idempotency", "outcome", outcome).increment();
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
