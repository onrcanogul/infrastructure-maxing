package com.inframaxing.paymentservice.model;

import com.inframaxing.paymentservice.exception.InvalidPaymentTransitionException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

	private final Payment payment = Payment.create(UUID.randomUUID(), new Money(100, "TRY"), "order-1");

	@Test
	void startsAsCreated() {
		assertThat(payment.status()).isEqualTo(PaymentStatus.CREATED);
		assertThat(payment.version()).isZero();
	}

	@Test
	void createdToAuthorizedIsValid() {
		payment.authorize("acme", "ref-1");

		assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
		assertThat(payment.providerCode()).isEqualTo("acme");
		assertThat(payment.providerRef()).isEqualTo("ref-1");
		assertThat(payment.updatedAt()).isAfterOrEqualTo(payment.createdAt());
	}

	@Test
	void capturedToCreatedThrows() {
		payment.authorize("acme", "ref-1");
		payment.capture();

		assertThatThrownBy(() -> payment.transitionTo(PaymentStatus.CREATED))
				.isInstanceOf(InvalidPaymentTransitionException.class)
				.hasMessageContaining("CAPTURED")
				.hasMessageContaining("CREATED");
		assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
	}

	@Test
	void cannotCaptureWithoutAuthorization() {
		assertThatThrownBy(payment::capture).isInstanceOf(InvalidPaymentTransitionException.class);
		assertThat(payment.status()).isEqualTo(PaymentStatus.CREATED);
	}

	@Test
	void failedTransitionLeavesStateUntouched() {
		payment.fail("acme", "insufficient_funds");

		assertThatThrownBy(() -> payment.authorize("acme", "ref-2"))
				.isInstanceOf(InvalidPaymentTransitionException.class);
		assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
		assertThat(payment.providerRef()).isNull();
		assertThat(payment.failureReason()).isEqualTo("insufficient_funds");
	}

	@Test
	void authorizedCanBeVoided() {
		payment.authorize("acme", "ref-1");
		payment.voidAuthorization();

		assertThat(payment.status()).isEqualTo(PaymentStatus.VOIDED);
	}
}
