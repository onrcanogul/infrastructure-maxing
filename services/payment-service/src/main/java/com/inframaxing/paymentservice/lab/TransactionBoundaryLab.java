package com.inframaxing.paymentservice.lab;

import com.inframaxing.paymentservice.model.Money;
import com.inframaxing.paymentservice.model.Payment;
import com.inframaxing.paymentservice.provider.PaymentProviderClient;
import com.inframaxing.paymentservice.provider.ProviderDecision;
import com.inframaxing.paymentservice.repository.PaymentRepository;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The same payment, authorized twice over: once with the provider call inside the
 * transaction and once outside it. The only difference is where the transaction starts and
 * ends - which is the whole experiment (notes/06-transaction-boundary.txt).
 *
 * <p>A transaction takes a connection from the pool when it begins and gives it back when
 * it commits. Whatever happens in between happens while holding one of the pool's ten
 * connections - including waiting for another company's server.
 */
@Service
@ConditionalOnProperty(name = "app.lab.enabled", havingValue = "true")
public class TransactionBoundaryLab {

	private final PaymentRepository payments;
	private final PaymentProviderClient provider;
	private final TransactionTemplate transactions;

	public TransactionBoundaryLab(PaymentRepository payments, PaymentProviderClient provider,
			PlatformTransactionManager transactionManager) {
		this.payments = payments;
		this.provider = provider;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	/**
	 * The mistake. One transaction around everything, so the connection is held for the
	 * insert, the provider's whole response time, and the update. The pool runs dry at
	 * roughly pool size / provider latency requests per second.
	 */
	public Payment providerCallInsideTransaction(UUID merchantId, Money money, String reference) {
		return transactions.execute(status -> {
			Payment payment = Payment.create(merchantId, money, reference);
			payments.insert(payment);
			ProviderDecision decision = provider.authorize(payment); // waiting on the network, connection held
			apply(payment, decision);
			return payments.update(payment);
		});
	}

	/**
	 * The fix. Two short transactions with the network call between them: the connection is
	 * held for a few milliseconds of SQL each time and never while waiting for the provider.
	 *
	 * <p>The price: if the provider call fails, the payment is left CREATED in the database.
	 * That is a state to reconcile (ask the provider, retry with the same key), not a
	 * reason to hold a connection.
	 */
	public Payment providerCallOutsideTransaction(UUID merchantId, Money money, String reference) {
		Payment payment = Payment.create(merchantId, money, reference);
		transactions.executeWithoutResult(status -> payments.insert(payment));
		ProviderDecision decision = provider.authorize(payment); // waiting on the network, no connection held
		apply(payment, decision);
		return transactions.execute(status -> payments.update(payment));
	}

	private void apply(Payment payment, ProviderDecision decision) {
		if (decision.outcome() == ProviderDecision.Outcome.APPROVED) {
			payment.authorize(provider.code(), decision.providerRef());
		} else {
			payment.fail(provider.code(), "declined by provider");
		}
	}
}
