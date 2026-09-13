package com.inframaxing.paymentservice.application;

import com.inframaxing.paymentservice.domain.Money;
import com.inframaxing.paymentservice.domain.Payment;
import com.inframaxing.paymentservice.domain.PaymentNotFound;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

	private final PaymentRepository repository;
	private final IdempotencyService idempotency;
	private final TransactionTemplate transactions;
	private final MeterRegistry meterRegistry;

	public PaymentService(PaymentRepository repository, IdempotencyService idempotency,
			PlatformTransactionManager transactionManager, MeterRegistry meterRegistry) {
		this.repository = repository;
		this.idempotency = idempotency;
		this.transactions = new TransactionTemplate(transactionManager);
		this.meterRegistry = meterRegistry;
	}

	public Creation create(UUID merchantId, String idempotencyKey, Money money, String reference) {
		String requestHash = idempotency.requestHash(merchantId, money, reference);

		Creation creation = transactions.execute(status -> {
			Optional<UUID> existing = idempotency.findPaymentId(merchantId, idempotencyKey, requestHash);
			if (existing.isPresent()) {
				return new Creation(get(existing.get()), true);
			}
			Payment payment = Payment.pending(merchantId, money, reference);
			repository.insert(payment);
			if (idempotency.claim(merchantId, idempotencyKey, requestHash, payment.id())) {
				return new Creation(payment, false);
			}
			status.setRollbackOnly();
			return null;
		});

		if (creation == null) {
			UUID winner = idempotency.findPaymentId(merchantId, idempotencyKey, requestHash)
					.orElseThrow(() -> new IllegalStateException("idempotency key vanished: " + idempotencyKey));
			return new Creation(get(winner), true);
		}

		if (!creation.replayed()) {
			meterRegistry.counter("payments.initiated", "currency", money.currency()).increment();
		}
		return creation;
	}

	public Payment get(UUID id) {
		return repository.findById(id).orElseThrow(() -> new PaymentNotFound(id));
	}

	public record Creation(Payment payment, boolean replayed) {
	}
}
