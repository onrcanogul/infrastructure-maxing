package com.inframaxing.paymentservice.exception;

import java.util.UUID;

public class ProviderCallFailedException extends RuntimeException {

	private final boolean timedOut;

	public ProviderCallFailedException(UUID paymentId, boolean timedOut, Throwable cause) {
		super(timedOut
				? "The provider did not answer in time for payment " + paymentId
				: "The provider call failed for payment " + paymentId, cause);
		this.timedOut = timedOut;
	}

	public boolean timedOut() {
		return timedOut;
	}
}
