package com.inframaxing.paymentservice.api;

import com.inframaxing.paymentservice.application.PaymentService;
import com.inframaxing.paymentservice.domain.Money;
import com.inframaxing.paymentservice.domain.Payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

	public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
	public static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping
	public ResponseEntity<PaymentResponse> create(
			@RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 64) String idempotencyKey,
			@Valid @RequestBody CreatePaymentRequest request) {
		PaymentService.Creation creation = paymentService.create(
				request.merchantId(),
				idempotencyKey,
				new Money(request.amountMinor(), request.currency()),
				request.reference());
		Payment payment = creation.payment();
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(payment.id())
				.toUri();
		return ResponseEntity.created(location)
				.header(IDEMPOTENT_REPLAYED, String.valueOf(creation.replayed()))
				.body(PaymentResponse.from(payment));
	}

	@GetMapping("/{id}")
	public PaymentResponse get(@PathVariable UUID id) {
		return PaymentResponse.from(paymentService.get(id));
	}
}
