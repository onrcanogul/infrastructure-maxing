package com.inframaxing.paymentservice.model;

import com.inframaxing.paymentservice.exception.InvalidPaymentTransitionException;

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
		Instant now = now();
		return new Payment(UUID.randomUUID(), merchantId, money, PaymentStatus.PENDING,
				reference, null, null, null, now, now, 0);
	}

	public Payment succeed(String providerCode, String providerRef) {
		Objects.requireNonNull(providerCode, "providerCode");
		Objects.requireNonNull(providerRef, "providerRef");
		return transition(PaymentStatus.SUCCEEDED, providerCode, providerRef, null);
	}

	public Payment fail(String providerCode, String failureReason) {
		Objects.requireNonNull(providerCode, "providerCode");
		Objects.requireNonNull(failureReason, "failureReason");
		return transition(PaymentStatus.FAILED, providerCode, providerRef, failureReason);
	}

	private Payment transition(PaymentStatus target, String providerCode, String providerRef, String failureReason) {
		if (status != PaymentStatus.PENDING) {
			throw new InvalidPaymentTransitionException(id, status, target);
		}
		return new Payment(id, merchantId, money, target, reference,
				providerCode, providerRef, failureReason, createdAt, now(), version);
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}
