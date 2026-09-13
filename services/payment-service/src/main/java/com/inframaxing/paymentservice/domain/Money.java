package com.inframaxing.paymentservice.domain;

import java.util.Currency;
import java.util.Objects;

public record Money(long amountMinor, String currency) {

	public Money {
		Objects.requireNonNull(currency, "currency");
		if (amountMinor <= 0) {
			throw new IllegalArgumentException("amountMinor must be positive");
		}
		try {
			currency = Currency.getInstance(currency).getCurrencyCode();
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("unknown currency: " + currency);
		}
	}
}
