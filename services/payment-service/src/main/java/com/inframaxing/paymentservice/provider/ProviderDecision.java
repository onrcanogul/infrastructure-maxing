package com.inframaxing.paymentservice.provider;

/**
 * What the payment provider answered for one authorization.
 *
 * @param approved  true when the provider authorized the payment
 * @param code      the provider's own result code
 * @param reference the provider's id for the authorization, when approved
 * @param reason    why it was declined, when not
 */
public record ProviderDecision(boolean approved, String code, String reference, String reason) {
}
