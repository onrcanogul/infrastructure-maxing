package com.inframaxing.paymentservice.controller;

import com.inframaxing.paymentservice.dto.CreatePaymentRequest;
import com.inframaxing.paymentservice.dto.PaymentResponse;
import com.inframaxing.paymentservice.model.Money;
import com.inframaxing.paymentservice.model.PaymentCreation;
import com.inframaxing.paymentservice.service.PaymentService;
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
@RequestMapping("/v1/payments")
public class PaymentController {

	public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping
	public ResponseEntity<PaymentResponse> create(
			@RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 64) String idempotencyKey,
			@Valid @RequestBody CreatePaymentRequest request) {
		PaymentCreation creation = paymentService.create(
				request.merchantId(),
				idempotencyKey,
				new Money(request.amountMinor(), request.currency()),
				request.reference());
		PaymentResponse body = PaymentResponse.from(creation.payment());
		if (creation.replayed()) {
			return ResponseEntity.ok(body);
		}
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(body.id())
				.toUri();
		return ResponseEntity.created(location).body(body);
	}

	@GetMapping("/{id}")
	public PaymentResponse get(@PathVariable UUID id) {
		return PaymentResponse.from(paymentService.get(id));
	}
}
