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
			"VOIDED,     VOIDED,     false",
			"CREATED,     AUTHORIZING, true",
			"CREATED,     TIMEOUT,     false",
			"AUTHORIZING, AUTHORIZED,  true",
			"AUTHORIZING, FAILED,      true",
			"AUTHORIZING, TIMEOUT,     true",
			"AUTHORIZING, CREATED,     false",
			"AUTHORIZING, AUTHORIZING, false",
			"AUTHORIZING, CAPTURED,    false",
			"AUTHORIZING, VOIDED,      false",
			"TIMEOUT,     AUTHORIZED,  false",
			"TIMEOUT,     FAILED,      false",
			"TIMEOUT,     TIMEOUT,     false",
			"TIMEOUT,     AUTHORIZING, false",
			"TIMEOUT,     CAPTURED,    false",
			"TIMEOUT,     VOIDED,      false",
			"TIMEOUT,     CREATED,     false",
			"AUTHORIZED,  TIMEOUT,     false",
			"CAPTURED,    TIMEOUT,     false",
			"FAILED,      TIMEOUT,     false",
			"VOIDED,      TIMEOUT,     false"
	})
	void transitionTable(PaymentStatus from, PaymentStatus to, boolean allowed) {
		assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
	}
}
