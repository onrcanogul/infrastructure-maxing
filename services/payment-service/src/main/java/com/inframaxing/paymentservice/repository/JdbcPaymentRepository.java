package com.inframaxing.paymentservice.repository;

import com.inframaxing.paymentservice.exception.PaymentVersionConflictException;
import com.inframaxing.paymentservice.model.Money;
import com.inframaxing.paymentservice.model.Payment;
import com.inframaxing.paymentservice.model.PaymentStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPaymentRepository implements PaymentRepository {

	private static final RowMapper<Payment> ROW_MAPPER = (rs, rowNum) -> new Payment(
			rs.getObject("id", UUID.class),
			rs.getObject("merchant_id", UUID.class),
			new Money(rs.getLong("amount_minor"), rs.getString("currency")),
			PaymentStatus.valueOf(rs.getString("status")),
			rs.getString("reference"),
			rs.getString("provider_code"),
			rs.getString("provider_ref"),
			rs.getString("failure_reason"),
			rs.getObject("created_at", OffsetDateTime.class).toInstant(),
			rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
			rs.getLong("version"));

	private final JdbcClient jdbc;

	public JdbcPaymentRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void insert(Payment payment) {
		jdbc.sql("""
				insert into payment (id, merchant_id, amount_minor, currency, status, reference,
				                     provider_code, provider_ref, failure_reason, created_at, updated_at, version)
				values (:id, :merchantId, :amountMinor, :currency, :status, :reference,
				        :providerCode, :providerRef, :failureReason, :createdAt, :updatedAt, :version)
				""")
				.param("id", payment.id())
				.param("merchantId", payment.merchantId())
				.param("amountMinor", payment.money().amountMinor())
				.param("currency", payment.money().currency())
				.param("status", payment.status().name())
				.param("reference", payment.reference())
				.param("providerCode", payment.providerCode())
				.param("providerRef", payment.providerRef())
				.param("failureReason", payment.failureReason())
				.param("createdAt", utc(payment.createdAt()))
				.param("updatedAt", utc(payment.updatedAt()))
				.param("version", payment.version())
				.update();
	}

	@Override
	public Payment update(Payment payment) {
		return jdbc.sql("""
				update payment
				set status = :status,
				    provider_code = :providerCode,
				    provider_ref = :providerRef,
				    failure_reason = :failureReason,
				    updated_at = :updatedAt,
				    version = version + 1
				where id = :id and version = :version
				returning id, merchant_id, amount_minor, currency, status, reference,
				          provider_code, provider_ref, failure_reason, created_at, updated_at, version
				""")
				.param("id", payment.id())
				.param("status", payment.status().name())
				.param("providerCode", payment.providerCode())
				.param("providerRef", payment.providerRef())
				.param("failureReason", payment.failureReason())
				.param("updatedAt", utc(payment.updatedAt()))
				.param("version", payment.version())
				.query(ROW_MAPPER)
				.optional()
				.orElseThrow(() -> new PaymentVersionConflictException(payment.id(), payment.version()));
	}

	@Override
	public Optional<Payment> findById(UUID id) {
		return jdbc.sql("""
				select id, merchant_id, amount_minor, currency, status, reference,
				       provider_code, provider_ref, failure_reason, created_at, updated_at, version
				from payment
				where id = :id
				""")
				.param("id", id)
				.query(ROW_MAPPER)
				.optional();
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}
}
