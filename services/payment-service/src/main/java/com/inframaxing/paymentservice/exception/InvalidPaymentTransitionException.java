package com.inframaxing.paymentservice.exception;

import com.inframaxing.paymentservice.model.PaymentStatus;

import java.util.UUID;

public class InvalidPaymentTransitionException extends RuntimeException {

	private final UUID paymentId;
	private final PaymentStatus from;
	private final PaymentStatus to;

	public InvalidPaymentTransitionException(UUID paymentId, PaymentStatus from, PaymentStatus to) {
		super("payment " + paymentId + " cannot move from " + from + " to " + to);
		this.paymentId = paymentId;
		this.from = from;
		this.to = to;
	}

	public UUID paymentId() {
		return paymentId;
	}

	public PaymentStatus from() {
		return from;
	}

	public PaymentStatus to() {
		return to;
	}
}
