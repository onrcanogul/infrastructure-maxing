package com.inframaxing.paymentservice.model;

public record PaymentCreation(Payment payment, boolean replayed) {
}
