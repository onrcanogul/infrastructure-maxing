package com.inframaxing.paymentservice.service;

import com.inframaxing.paymentservice.exception.PaymentNotFound;
import com.inframaxing.paymentservice.model.Money;
import com.inframaxing.paymentservice.model.Payment;
import com.inframaxing.paymentservice.model.PaymentCreation;
import com.inframaxing.paymentservice.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

	public PaymentCreation create(UUID merchantId, String idempotencyKey, Money money, String reference) {
		String requestHash = idempotency.requestHash(merchantId, money, reference);
		Payment payment = Payment.pending(merchantId, money, reference);

		try {
			transactions.executeWithoutResult(status -> {
				repository.insert(payment);
				idempotency.register(merchantId, idempotencyKey, requestHash, payment.id());
			});
		} catch (DuplicateKeyException e) {
			UUID existing = idempotency.replay(merchantId, idempotencyKey, requestHash);
			return new PaymentCreation(get(existing), true);
		}

		meterRegistry.counter("payments.initiated", "currency", money.currency()).increment();
		return new PaymentCreation(payment, false);
	}

	public Payment get(UUID id) {
		return repository.findById(id).orElseThrow(() -> new PaymentNotFound(id));
	}
}
