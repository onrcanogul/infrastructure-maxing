package com.inframaxing.paymentservice.model;

import com.inframaxing.paymentservice.exception.InvalidPaymentTransitionException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public class Payment {

	private final UUID id;
	private final UUID merchantId;
	private final Money money;
	private final String reference;
	private final Instant createdAt;
	private final long version;
	private PaymentStatus status;
	private String providerCode;
	private String providerRef;
	private String failureReason;
	private Instant updatedAt;

	private Payment(UUID id, UUID merchantId, Money money, PaymentStatus status, String reference,
			String providerCode, String providerRef, String failureReason,
			Instant createdAt, Instant updatedAt, long version) {
		this.id = Objects.requireNonNull(id, "id");
		this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
		this.money = Objects.requireNonNull(money, "money");
		this.status = Objects.requireNonNull(status, "status");
		this.reference = reference;
		this.providerCode = providerCode;
		this.providerRef = providerRef;
		this.failureReason = failureReason;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
		this.version = version;
	}

	public static Payment create(UUID merchantId, Money money, String reference) {
		Instant now = now();
		return new Payment(UUID.randomUUID(), merchantId, money, PaymentStatus.CREATED,
				reference, null, null, null, now, now, 0);
	}

	public static Payment restore(UUID id, UUID merchantId, Money money, PaymentStatus status, String reference,
			String providerCode, String providerRef, String failureReason,
			Instant createdAt, Instant updatedAt, long version) {
		return new Payment(id, merchantId, money, status, reference,
				providerCode, providerRef, failureReason, createdAt, updatedAt, version);
	}

	public void startAuthorization() {
		transitionTo(PaymentStatus.AUTHORIZING);
	}

	public void authorize(String providerCode, String providerRef) {
		Objects.requireNonNull(providerCode, "providerCode");
		Objects.requireNonNull(providerRef, "providerRef");
		transitionTo(PaymentStatus.AUTHORIZED);
		this.providerCode = providerCode;
		this.providerRef = providerRef;
	}

	public void capture() {
		transitionTo(PaymentStatus.CAPTURED);
	}

	public void voidAuthorization() {
		transitionTo(PaymentStatus.VOIDED);
	}

	public void fail(String providerCode, String failureReason) {
		Objects.requireNonNull(providerCode, "providerCode");
		Objects.requireNonNull(failureReason, "failureReason");
		transitionTo(PaymentStatus.FAILED);
		this.providerCode = providerCode;
		this.failureReason = failureReason;
	}

	void transitionTo(PaymentStatus target) {
		Objects.requireNonNull(target, "target");
		if (!status.canTransitionTo(target)) {
			throw new InvalidPaymentTransitionException(id, status, target);
		}
		this.status = target;
		this.updatedAt = now();
	}

	public UUID id() {
		return id;
	}

	public UUID merchantId() {
		return merchantId;
	}

	public Money money() {
		return money;
	}

	public PaymentStatus status() {
		return status;
	}

	public String reference() {
		return reference;
	}

	public String providerCode() {
		return providerCode;
	}

	public String providerRef() {
		return providerRef;
	}

	public String failureReason() {
		return failureReason;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}

	public long version() {
		return version;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof Payment other && id.equals(other.id));
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public String toString() {
		return "Payment[id=" + id + ", status=" + status + ", version=" + version + "]";
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}
