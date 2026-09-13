package com.inframaxing.paymentservice.provider;

import com.inframaxing.paymentservice.model.Payment;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The payment provider, over HTTP.
 *
 * <p>A network call: it can take as long as the provider likes, which is why it must never
 * run while a database connection is held (notes/06-transaction-boundary.txt), and why its
 * timeouts are set explicitly (notes/05-timeouts.txt) instead of left at "forever".
 */
@Component
public class PaymentProviderClient {

	private final RestClient provider;

	public PaymentProviderClient(RestClient paymentProviderRestClient) {
		this.provider = paymentProviderRestClient;
	}

	public ProviderDecision authorize(Payment payment) {
		return provider.post()
				.uri("/authorize")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of(
						"paymentId", payment.id(),
						"amountMinor", payment.money().amountMinor(),
						"currency", payment.money().currency()))
				.retrieve()
				.body(ProviderDecision.class);
	}
}
