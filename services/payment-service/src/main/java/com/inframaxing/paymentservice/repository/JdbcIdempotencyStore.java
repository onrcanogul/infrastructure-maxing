package com.inframaxing.paymentservice.repository;

import com.inframaxing.paymentservice.model.IdempotencyRecord;
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

	public void insert(UUID merchantId, String key, String requestHash, UUID paymentId) {
		jdbc.sql("""
				insert into idempotency_key (merchant_id, idem_key, request_hash, payment_id)
				values (:merchantId, :key, :requestHash, :paymentId)
				""")
				.param("merchantId", merchantId)
				.param("key", key)
				.param("requestHash", requestHash)
				.param("paymentId", paymentId)
				.update();
	}

	public Optional<IdempotencyRecord> find(UUID merchantId, String key) {
		return jdbc.sql("""
				select request_hash, payment_id
				from idempotency_key
				where merchant_id = :merchantId and idem_key = :key
				""")
				.param("merchantId", merchantId)
				.param("key", key)
				.query((rs, rowNum) -> new IdempotencyRecord(rs.getString("request_hash"), rs.getObject("payment_id", UUID.class)))
				.optional();
	}
}
