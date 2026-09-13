package com.inframaxing.paymentservice.provider;

import com.inframaxing.paymentservice.exception.ProviderCallFailedException;
import com.inframaxing.paymentservice.model.Payment;
import java.net.http.HttpTimeoutException;
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

	public PaymentProviderClient(RestClient paymentProviderRestClient,
			@Value("${app.provider.code}") String code) {
		this.provider = paymentProviderRestClient;
		this.code = code;
	}

	public String code() {
		return code;
	}

	public ProviderDecision authorize(Payment payment) {
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
			return decision;
		}
		catch (ResourceAccessException e) {
			throw new ProviderCallFailedException(payment.id(), isTimeout(e), e);
		}
		catch (RestClientException e) {
			throw new ProviderCallFailedException(payment.id(), false, e);
		}
	}

	private static boolean isTimeout(ResourceAccessException e) {
		return e.getCause() instanceof HttpTimeoutException;
	}
}
