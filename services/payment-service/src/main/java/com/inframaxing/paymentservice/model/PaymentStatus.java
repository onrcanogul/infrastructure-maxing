package com.inframaxing.paymentservice.model;

public enum PaymentStatus {
	CREATED,
	AUTHORIZING,
	AUTHORIZED,
	CAPTURED,
	FAILED,
	VOIDED;

	public boolean canTransitionTo(PaymentStatus target) {
		return switch (this) {
			case CREATED -> target == AUTHORIZING || target == AUTHORIZED || target == FAILED;
			case AUTHORIZING -> target == AUTHORIZED || target == FAILED;
			case AUTHORIZED -> target == CAPTURED || target == VOIDED || target == FAILED;
			case CAPTURED, FAILED, VOIDED -> false;
		};
	}
}
