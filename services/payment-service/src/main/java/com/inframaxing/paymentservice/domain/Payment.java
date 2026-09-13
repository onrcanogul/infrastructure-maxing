package com.inframaxing.paymentservice.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public record Payment(
		UUID id,
		UUID merchantId,
		Money money,
		PaymentStatus status,
		String reference,
		String providerCode,
		String providerRef,
		String failureReason,
		Instant createdAt,
		Instant updatedAt,
		long version
) {

	public Payment {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(merchantId, "merchantId");
		Objects.requireNonNull(money, "money");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(updatedAt, "updatedAt");
	}

	public static Payment pending(UUID merchantId, Money money, String reference) {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		return new Payment(UUID.randomUUID(), merchantId, money, PaymentStatus.PENDING,
				reference, null, null, null, now, now, 0);
	}
}
