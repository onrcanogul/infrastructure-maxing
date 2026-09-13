package com.inframaxing.paymentservice.provider;

public record ProviderDecision(String providerRef, Outcome outcome) {

	public enum Outcome {
		APPROVED,
		DECLINED
	}
}
