package com.inframaxing.paymentservice.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreatePaymentRequest(
		@NotNull UUID merchantId,
		@NotNull @Positive Long amountMinor,
		@NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
		@Size(min = 1, max = 64) String reference
) {
}
