package com.inframaxing.paymentservice.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {

	@ParameterizedTest
	@CsvSource({
			"CREATED,    CREATED,    false",
			"CREATED,    AUTHORIZED, true",
			"CREATED,    CAPTURED,   false",
			"CREATED,    FAILED,     true",
			"CREATED,    VOIDED,     false",
			"AUTHORIZED, CREATED,    false",
			"AUTHORIZED, AUTHORIZED, false",
			"AUTHORIZED, CAPTURED,   true",
			"AUTHORIZED, FAILED,     true",
			"AUTHORIZED, VOIDED,     true",
			"CAPTURED,   CREATED,    false",
			"CAPTURED,   AUTHORIZED, false",
			"CAPTURED,   CAPTURED,   false",
			"CAPTURED,   FAILED,     false",
			"CAPTURED,   VOIDED,     false",
			"FAILED,     CREATED,    false",
			"FAILED,     AUTHORIZED, false",
			"FAILED,     CAPTURED,   false",
			"FAILED,     FAILED,     false",
			"FAILED,     VOIDED,     false",
			"VOIDED,     CREATED,    false",
			"VOIDED,     AUTHORIZED, false",
			"VOIDED,     CAPTURED,   false",
			"VOIDED,     FAILED,     false",
			"VOIDED,     VOIDED,     false"
	})
	void transitionTable(PaymentStatus from, PaymentStatus to, boolean allowed) {
		assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
	}
}
