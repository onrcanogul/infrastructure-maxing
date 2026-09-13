package com.inframaxing.paymentservice.model;

import com.inframaxing.paymentservice.exception.InvalidPaymentTransitionException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

	private final Payment pending = Payment.pending(UUID.randomUUID(), new Money(100, "TRY"), "order-1");

	@Test
	void succeedsFromPendingKeepingExpectedVersion() {
		Payment succeeded = pending.succeed("acme", "ref-1");

		assertThat(succeeded.status()).isEqualTo(PaymentStatus.SUCCEEDED);
		assertThat(succeeded.providerCode()).isEqualTo("acme");
		assertThat(succeeded.providerRef()).isEqualTo("ref-1");
		assertThat(succeeded.version()).isEqualTo(pending.version());
		assertThat(succeeded.updatedAt()).isAfterOrEqualTo(pending.updatedAt());
	}

	@Test
	void failsFromPending() {
		Payment failed = pending.fail("acme", "insufficient_funds");

		assertThat(failed.status()).isEqualTo(PaymentStatus.FAILED);
		assertThat(failed.failureReason()).isEqualTo("insufficient_funds");
		assertThat(failed.version()).isEqualTo(pending.version());
	}

	@Test
	void rejectsTransitionFromFinalStatus() {
		Payment succeeded = pending.succeed("acme", "ref-1");

		assertThatThrownBy(() -> succeeded.fail("acme", "timeout"))
				.isInstanceOf(InvalidPaymentTransitionException.class);
		assertThatThrownBy(() -> succeeded.succeed("acme", "ref-2"))
				.isInstanceOf(InvalidPaymentTransitionException.class);
	}
}
