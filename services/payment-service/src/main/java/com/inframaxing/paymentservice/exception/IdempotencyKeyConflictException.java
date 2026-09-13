package com.inframaxing.paymentservice.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

	private final String idempotencyKey;

	public IdempotencyKeyConflictException(String idempotencyKey) {
		super("idempotency key already used with a different request: " + idempotencyKey);
		this.idempotencyKey = idempotencyKey;
	}

	public String idempotencyKey() {
		return idempotencyKey;
	}
}
