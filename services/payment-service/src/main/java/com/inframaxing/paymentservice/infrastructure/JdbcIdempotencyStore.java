package com.inframaxing.paymentservice.infrastructure;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcIdempotencyStore {

	private final JdbcClient jdbc;

	public JdbcIdempotencyStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<Entry> find(UUID merchantId, String key) {
		return jdbc.sql("""
				select request_hash, payment_id
				from idempotency_key
				where merchant_id = :merchantId and idem_key = :key
				""")
				.param("merchantId", merchantId)
				.param("key", key)
				.query((rs, rowNum) -> new Entry(rs.getString("request_hash"), rs.getObject("payment_id", UUID.class)))
				.optional();
	}

	public boolean insertIfAbsent(UUID merchantId, String key, String requestHash, UUID paymentId) {
		return jdbc.sql("""
				insert into idempotency_key (merchant_id, idem_key, request_hash, payment_id)
				values (:merchantId, :key, :requestHash, :paymentId)
				on conflict (merchant_id, idem_key) do nothing
				""")
				.param("merchantId", merchantId)
				.param("key", key)
				.param("requestHash", requestHash)
				.param("paymentId", paymentId)
				.update() == 1;
	}

	public record Entry(String requestHash, UUID paymentId) {
	}
}
