package com.inframaxing.paymentservice.api;

import com.inframaxing.paymentservice.domain.Payment;
import com.inframaxing.paymentservice.domain.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
		UUID id,
		UUID merchantId,
		long amountMinor,
		String currency,
		PaymentStatus status,
		String reference,
		String providerCode,
		String providerRef,
		String failureReason,
		Instant createdAt,
		Instant updatedAt
) {

	public static PaymentResponse from(Payment payment) {
		return new PaymentResponse(
				payment.id(),
				payment.merchantId(),
				payment.money().amountMinor(),
				payment.money().currency(),
				payment.status(),
				payment.reference(),
				payment.providerCode(),
				payment.providerRef(),
				payment.failureReason(),
				payment.createdAt(),
				payment.updatedAt());
	}
}
