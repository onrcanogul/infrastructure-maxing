package com.inframaxing.paymentservice.repository;

import com.inframaxing.paymentservice.model.Payment;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

	void insert(Payment payment);

	Optional<Payment> findById(UUID id);
}
