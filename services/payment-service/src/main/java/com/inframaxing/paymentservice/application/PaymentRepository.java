package com.inframaxing.paymentservice.application;

import com.inframaxing.paymentservice.domain.Payment;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

	void insert(Payment payment);

	Optional<Payment> findById(UUID id);
}
