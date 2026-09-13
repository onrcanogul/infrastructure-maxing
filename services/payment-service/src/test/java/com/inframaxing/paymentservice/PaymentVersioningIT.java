package com.inframaxing.paymentservice;

import com.inframaxing.paymentservice.exception.PaymentVersionConflictException;
import com.inframaxing.paymentservice.model.Money;
import com.inframaxing.paymentservice.model.Payment;
import com.inframaxing.paymentservice.model.PaymentStatus;
import com.inframaxing.paymentservice.repository.PaymentRepository;
import com.inframaxing.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentVersioningIT extends IntegrationTestSupport {

	@Autowired
	PaymentService paymentService;

	@Autowired
	PaymentRepository paymentRepository;

	@Test
	void staleVersionUpdateIsRejected() {
		UUID id = createPayment();
		Payment stale = paymentService.get(id);

		Payment authorized = paymentService.authorize(id, "acme", "ref-1");
		stale.fail("acme", "timeout");

		assertThat(authorized.version()).isEqualTo(1);
		assertThatThrownBy(() -> paymentRepository.update(stale))
				.isInstanceOf(PaymentVersionConflictException.class);
		assertThat(paymentService.get(id))
				.extracting(Payment::status, Payment::version)
				.containsExactly(PaymentStatus.AUTHORIZED, 1L);
	}

	@Test
	void fullLifecycleIncrementsVersion() {
		UUID id = createPayment();

		paymentService.authorize(id, "acme", "ref-1");
		Payment captured = paymentService.capture(id);

		assertThat(captured)
				.extracting(Payment::status, Payment::providerRef, Payment::version)
				.containsExactly(PaymentStatus.CAPTURED, "ref-1", 2L);
	}

	@Test
	void concurrentUpdatesOnSameVersionLetExactlyOneWin() {
		UUID id = createPayment();
		List<Payment> copies = IntStream.range(0, 16).mapToObj(i -> paymentService.get(id)).toList();
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Payment>> futures;

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			futures = IntStream.range(0, 16)
					.mapToObj(i -> executor.submit(() -> {
						Payment copy = copies.get(i);
						if (i % 2 == 0) {
							copy.authorize("acme", "ref-" + i);
						} else {
							copy.fail("acme", "declined-" + i);
						}
						start.await();
						return paymentRepository.update(copy);
					}))
					.toList();
			start.countDown();
		}

		long winners = futures.stream().filter(f -> f.state() == Future.State.SUCCESS).count();
		List<Throwable> losers = futures.stream()
				.filter(f -> f.state() == Future.State.FAILED)
				.map(Future::exceptionNow)
				.toList();

		assertThat(winners).isEqualTo(1);
		assertThat(losers).hasSize(15).allMatch(PaymentVersionConflictException.class::isInstance);
		assertThat(paymentService.get(id).version()).isEqualTo(1);
	}

	private UUID createPayment() {
		return paymentService.create(UUID.randomUUID(), "versioning", new Money(100, "TRY"), null).payment().id();
	}
}
