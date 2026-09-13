package com.inframaxing.paymentservice.provider;

import com.inframaxing.paymentservice.exception.ProviderCallFailedException;
import com.inframaxing.paymentservice.metrics.ProviderMetrics;
import com.inframaxing.paymentservice.model.Payment;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PaymentProviderClient {

	public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private final RestClient provider;

	private final String code;

	private final ProviderMetrics metrics;

	public PaymentProviderClient(RestClient paymentProviderRestClient,
			@Value("${app.provider.code}") String code, ProviderMetrics metrics) {
		this.provider = paymentProviderRestClient;
		this.code = code;
		this.metrics = metrics;
	}

	public String code() {
		return code;
	}

	public ProviderDecision authorize(Payment payment) {
		long startedAt = System.nanoTime();
		ProviderMetrics.Outcome outcome = ProviderMetrics.Outcome.ERROR;
		try {
			ProviderDecision decision = provider.post()
					.uri("/providers/{code}/authorize", code)
					.header(IDEMPOTENCY_KEY, payment.id().toString())
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of(
							"paymentId", payment.id(),
							"amountMinor", payment.money().amountMinor(),
							"currency", payment.money().currency()))
					.retrieve()
					.body(ProviderDecision.class);

			if (decision == null || decision.outcome() == null) {
				throw new ProviderCallFailedException(payment.id(), false, null);
			}
			outcome = decision.outcome() == ProviderDecision.Outcome.APPROVED
					? ProviderMetrics.Outcome.APPROVED
					: ProviderMetrics.Outcome.DECLINED;
			return decision;
		}
		catch (ResourceAccessException e) {
			boolean timedOut = isTimeout(e);
			outcome = timedOut ? ProviderMetrics.Outcome.TIMEOUT : ProviderMetrics.Outcome.ERROR;
			throw new ProviderCallFailedException(payment.id(), timedOut, e);
		}
		catch (RestClientException e) {
			throw new ProviderCallFailedException(payment.id(), false, e);
		}
		finally {
			metrics.call(code, outcome, Duration.ofNanos(System.nanoTime() - startedAt));
		}
	}

	private static boolean isTimeout(ResourceAccessException e) {
		return e.getCause() instanceof HttpTimeoutException;
	}
}
