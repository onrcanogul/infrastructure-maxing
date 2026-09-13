package com.inframaxing.paymentservice.model;

public enum PaymentStatus {
	CREATED,
	AUTHORIZING,
	AUTHORIZED,
	CAPTURED,
	FAILED,
	TIMEOUT,
	VOIDED;

	public boolean canTransitionTo(PaymentStatus target) {
		return switch (this) {
			case CREATED -> target == AUTHORIZING || target == AUTHORIZED || target == FAILED;
			case AUTHORIZING -> target == AUTHORIZED || target == FAILED || target == TIMEOUT;
			case AUTHORIZED -> target == CAPTURED || target == VOIDED || target == FAILED;
			case CAPTURED, FAILED, TIMEOUT, VOIDED -> false;
		};
	}
}
