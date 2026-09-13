package com.inframaxing.paymentservice.service;

import com.inframaxing.paymentservice.exception.IdempotencyKeyConflictException;
import com.inframaxing.paymentservice.exception.PaymentNotFoundException;
import com.inframaxing.paymentservice.metrics.PaymentMetrics;
import com.inframaxing.paymentservice.model.Money;
import com.inframaxing.paymentservice.model.Payment;
import com.inframaxing.paymentservice.model.PaymentCreation;
import com.inframaxing.paymentservice.provider.PaymentProviderClient;
import com.inframaxing.paymentservice.provider.ProviderDecision;
import com.inframaxing.paymentservice.repository.PaymentRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Consumer;

@Service
public class PaymentService {

	private final PaymentRepository repository;
	private final IdempotencyService idempotency;
	private final TransactionTemplate transactions;
	private final PaymentMetrics metrics;
	private final PaymentProviderClient provider;

	public PaymentService(PaymentRepository repository, IdempotencyService idempotency,
			PlatformTransactionManager transactionManager, PaymentMetrics metrics,
			PaymentProviderClient provider) {
		this.repository = repository;
		this.idempotency = idempotency;
		this.transactions = new TransactionTemplate(transactionManager);
		this.metrics = metrics;
		this.provider = provider;
	}

	public PaymentCreation create(UUID merchantId, String idempotencyKey, Money money, String reference) {
		String requestHash = idempotency.requestHash(merchantId, money, reference);
		Payment payment = Payment.create(merchantId, money, reference);

		try {
			transactions.executeWithoutResult(status -> {
				repository.insert(payment);
				idempotency.register(merchantId, idempotencyKey, requestHash, payment.id());
				applyProviderDecision(payment);
				repository.update(payment);
			});
		} catch (DuplicateKeyException e) {
			return replay(merchantId, idempotencyKey, requestHash);
		}

		metrics.created(money);
		return new PaymentCreation(payment, false);
	}

	private void applyProviderDecision(Payment payment) {
		ProviderDecision decision = provider.authorize(payment);
		if (decision.outcome() == ProviderDecision.Outcome.APPROVED) {
			payment.authorize(provider.code(), decision.providerRef());
		} else {
			payment.fail(provider.code(), "declined by provider");
		}
	}

	public Payment authorize(UUID id, String providerCode, String providerRef) {
		return change(id, payment -> payment.authorize(providerCode, providerRef));
	}

	public Payment capture(UUID id) {
		return change(id, Payment::capture);
	}

	public Payment voidAuthorization(UUID id) {
		return change(id, Payment::voidAuthorization);
	}

	public Payment fail(UUID id, String providerCode, String failureReason) {
		return change(id, payment -> payment.fail(providerCode, failureReason));
	}

	public Payment get(UUID id) {
		return repository.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
	}

	private PaymentCreation replay(UUID merchantId, String idempotencyKey, String requestHash) {
		UUID existing;
		try {
			existing = idempotency.replay(merchantId, idempotencyKey, requestHash);
		} catch (IdempotencyKeyConflictException e) {
			metrics.conflict();
			throw e;
		}
		metrics.replayed();
		return new PaymentCreation(get(existing), true);
	}

	private Payment change(UUID id, Consumer<Payment> transition) {
		Payment payment = get(id);
		transition.accept(payment);
		return repository.update(payment);
	}
}
