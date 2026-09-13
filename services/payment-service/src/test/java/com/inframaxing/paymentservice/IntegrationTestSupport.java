package com.inframaxing.paymentservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestSupport {

	@Autowired
	protected RestTestClient client;

	@Autowired
	protected JdbcClient jdbc;

	protected RestTestClient.ResponseSpec postPayment(String idempotencyKey, Map<String, Object> body) {
		return client.post().uri("/v1/payments")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.exchange();
	}

	protected int paymentCount(UUID merchantId) {
		return jdbc.sql("select count(*) from payment where merchant_id = :merchantId")
				.param("merchantId", merchantId)
				.query(Integer.class)
				.single();
	}

	protected int idempotencyKeyCount(UUID merchantId) {
		return jdbc.sql("select count(*) from idempotency_key where merchant_id = :merchantId")
				.param("merchantId", merchantId)
				.query(Integer.class)
				.single();
	}

	protected static Map<String, Object> paymentRequest(UUID merchantId, long amountMinor, String currency, String reference) {
		Map<String, Object> body = new HashMap<>();
		body.put("merchantId", merchantId);
		body.put("amountMinor", amountMinor);
		body.put("currency", currency);
		body.put("reference", reference);
		return body;
	}
}
