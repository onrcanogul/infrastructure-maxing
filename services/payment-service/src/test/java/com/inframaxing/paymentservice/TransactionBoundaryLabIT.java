package com.inframaxing.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * The transaction-boundary experiment, made into an assertion: while the provider is thinking,
 * how many of the pool's connections are in use?
 *
 * <p>The provider here answers only when the test lets it, so "while it is thinking" is a
 * moment the test controls. The load numbers are in notes/06-transaction-boundary.txt; this
 * is the mechanism behind them.
 */
@TestPropertySource(properties = "app.lab.enabled=true")
class TransactionBoundaryLabIT extends IntegrationTestSupport {

	private static final HttpServer PROVIDER = startProvider();
	private static volatile CountDownLatch providerCalled;
	private static volatile CountDownLatch providerMayAnswer;

	@Autowired
	private DataSource dataSource;

	@DynamicPropertySource
	static void provider(DynamicPropertyRegistry registry) {
		registry.add("app.provider.base-url", () -> "http://localhost:" + PROVIDER.getAddress().getPort());
	}

	@BeforeEach
	void resetProvider() {
		providerCalled = new CountDownLatch(1);
		providerMayAnswer = new CountDownLatch(1);
	}

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
		CompletableFuture<Map<String, Object>> response = CompletableFuture.supplyAsync(() -> client.post().uri(path)
				.contentType(MediaType.APPLICATION_JSON)
				.body(paymentRequest(UUID.randomUUID(), 1999, "EUR", "lab"))
				.exchange()
				.expectStatus().isCreated()
				.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
				})
				.returnResult()
				.getResponseBody());

		assertThat(providerCalled.await(10, TimeUnit.SECONDS)).as("the provider was called").isTrue();
		int inUse = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();

		providerMayAnswer.countDown();
		assertThat(response.get(10, TimeUnit.SECONDS)).containsEntry("status", "AUTHORIZED");
		return inUse;
	}

	private static HttpServer startProvider() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/authorize", exchange -> {
				providerCalled.countDown();
				try {
					providerMayAnswer.await(10, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				byte[] body = "{\"approved\":true,\"code\":\"00\",\"reference\":\"stub-auth\"}"
						.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(body);
				}
			});
			server.setExecutor(Executors.newCachedThreadPool());
			server.start();
			return server;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
