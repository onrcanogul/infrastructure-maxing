package com.inframaxing.paymentservice.model;

public enum PaymentStatus {
	CREATED,
	AUTHORIZED,
	CAPTURED,
	FAILED,
	VOIDED;

	public boolean canTransitionTo(PaymentStatus target) {
		return switch (this) {
			case CREATED -> target == AUTHORIZED || target == FAILED;
			case AUTHORIZED -> target == CAPTURED || target == VOIDED || target == FAILED;
			case CAPTURED, FAILED, VOIDED -> false;
		};
	}
}
