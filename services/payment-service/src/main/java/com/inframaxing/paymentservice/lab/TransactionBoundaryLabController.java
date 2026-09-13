package com.inframaxing.paymentservice.lab;

import com.inframaxing.paymentservice.dto.CreatePaymentRequest;
import com.inframaxing.paymentservice.dto.PaymentResponse;
import com.inframaxing.paymentservice.model.Money;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The transaction-boundary experiment over HTTP, for a load generator to hit
 * (load/run-tx-boundary.sh). Off unless PAYROUTE_LAB_ENABLED=true.
 */
@RestController
@RequestMapping("/lab/tx-boundary")
@ConditionalOnProperty(name = "app.lab.enabled", havingValue = "true")
public class TransactionBoundaryLabController {

	private final TransactionBoundaryLab lab;

	public TransactionBoundaryLabController(TransactionBoundaryLab lab) {
		this.lab = lab;
	}

	@PostMapping("/inside")
	@ResponseStatus(HttpStatus.CREATED)
	public PaymentResponse inside(@Valid @RequestBody CreatePaymentRequest request) {
		return PaymentResponse.from(lab.providerCallInsideTransaction(request.merchantId(),
				new Money(request.amountMinor(), request.currency()), request.reference()));
	}

	@PostMapping("/outside")
	@ResponseStatus(HttpStatus.CREATED)
	public PaymentResponse outside(@Valid @RequestBody CreatePaymentRequest request) {
		return PaymentResponse.from(lab.providerCallOutsideTransaction(request.merchantId(),
				new Money(request.amountMinor(), request.currency()), request.reference()));
	}
}
