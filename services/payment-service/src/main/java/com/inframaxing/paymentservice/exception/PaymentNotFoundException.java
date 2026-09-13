package com.inframaxing.paymentservice.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {

	private final UUID paymentId;

	public PaymentNotFoundException(UUID paymentId) {
		super("payment not found: " + paymentId);
		this.paymentId = paymentId;
	}

	public UUID paymentId() {
		return paymentId;
	}
}
