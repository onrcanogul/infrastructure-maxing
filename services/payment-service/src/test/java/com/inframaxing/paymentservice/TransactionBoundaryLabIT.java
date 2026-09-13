package com.inframaxing.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * The transaction-boundary experiment, made into an assertion: while the provider is thinking,
 * how many of the pool's connections are in use?
 *
 * <p>The provider here answers only when the test lets it, so "while it is thinking" is a
 * moment the test controls. The load numbers are in notes/06-transaction-boundary.md; this
 * is the mechanism behind them.
 */
@TestPropertySource(properties = "app.lab.enabled=true")
class TransactionBoundaryLabIT extends IntegrationTestSupport {

	@Autowired
	private DataSource dataSource;

	@Test
	void insideTheTransactionAConnectionIsHeldForTheWholeProviderCall() throws Exception {
		assertThat(connectionsInUseWhileProviderWaits("/lab/tx-boundary/inside")).isEqualTo(1);
	}

	@Test
	void outsideTheTransactionNoConnectionIsHeldWhileWaiting() throws Exception {
		assertThat(connectionsInUseWhileProviderWaits("/lab/tx-boundary/outside")).isZero();
	}

	/** Starts a payment, freezes the provider mid-call, counts busy connections, then lets it finish. */
	private int connectionsInUseWhileProviderWaits(String path) throws Exception {
		ProviderStub.answerOnlyWhenReleased();

		CompletableFuture<Map<String, Object>> response = CompletableFuture.supplyAsync(() -> client.post().uri(path)
				.contentType(MediaType.APPLICATION_JSON)
				.body(paymentRequest(UUID.randomUUID(), 1999, "EUR", "lab"))
				.exchange()
				.expectStatus().isCreated()
				.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
				})
				.returnResult()
				.getResponseBody());

		assertThat(ProviderStub.awaitCall()).as("the provider was called").isTrue();
		int inUse = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();

		ProviderStub.release();
		assertThat(response.get(10, TimeUnit.SECONDS)).containsEntry("status", "AUTHORIZED");
		return inUse;
	}
}
