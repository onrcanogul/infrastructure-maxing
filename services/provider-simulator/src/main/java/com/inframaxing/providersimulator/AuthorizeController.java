package com.inframaxing.providersimulator;

import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one endpoint that matters: authorize a payment, slowly.
 *
 * <p>The response shape is what PaymentProviderClient in payment-service reads back, and matches
 * the WireMock stub this service replaces.
 */
@RestController
public class AuthorizeController {

	private final SimulatorSettings settings;

	public AuthorizeController(SimulatorSettings settings) {
		this.settings = settings;
	}

	@PostMapping("/authorize")
	public Decision authorize(@RequestBody AuthorizeRequest request) throws InterruptedException {
		// The point of the whole service. Virtual threads make this cheap to hold at high rates.
		Thread.sleep(settings.latency());

		return settings.approve()
				? new Decision(true, "00", "sim-" + UUID.randomUUID(), null)
				: new Decision(false, "51", null, "insufficient funds");
	}

	/**
	 * What payment-service sends. Fields it does not send are simply absent - the simulator does not
	 * validate its caller, because a real provider's validation is not what we are measuring.
	 */
	public record AuthorizeRequest(UUID paymentId, long amountMinor, String currency) {
	}

	/** What payment-service reads back, field for field. */
	public record Decision(boolean approved, String code, String reference, String reason) {
	}
}
