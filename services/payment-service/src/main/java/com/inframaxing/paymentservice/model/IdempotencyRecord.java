package com.inframaxing.paymentservice.model;

import java.util.UUID;

public record IdempotencyRecord(String requestHash, UUID paymentId) {
}
