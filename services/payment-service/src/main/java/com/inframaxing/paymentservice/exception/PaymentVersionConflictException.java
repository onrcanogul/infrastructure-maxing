package com.inframaxing.paymentservice.exception;

import java.util.UUID;

public class PaymentVersionConflictException extends RuntimeException {

	private final UUID paymentId;
	private final long expectedVersion;

	public PaymentVersionConflictException(UUID paymentId, long expectedVersion) {
		super("payment " + paymentId + " was modified concurrently, expected version " + expectedVersion);
		this.paymentId = paymentId;
		this.expectedVersion = expectedVersion;
	}

	public UUID paymentId() {
		return paymentId;
	}

	public long expectedVersion() {
		return expectedVersion;
	}
}
