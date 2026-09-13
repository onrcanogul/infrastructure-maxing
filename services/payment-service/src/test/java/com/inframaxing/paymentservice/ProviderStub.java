package com.inframaxing.paymentservice;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class ProviderStub {

	private static final HttpServer SERVER = start();

	private static volatile CountDownLatch called = new CountDownLatch(1);
	private static volatile CountDownLatch mayAnswer = new CountDownLatch(0);

	private ProviderStub() {
	}

	static String baseUrl() {
		return "http://localhost:" + SERVER.getAddress().getPort();
	}

	static void answerImmediately() {
		called = new CountDownLatch(1);
		mayAnswer = new CountDownLatch(0);
	}

	static void answerOnlyWhenReleased() {
		called = new CountDownLatch(1);
		mayAnswer = new CountDownLatch(1);
	}

	static boolean awaitCall() throws InterruptedException {
		return called.await(10, TimeUnit.SECONDS);
	}

	static void release() {
		mayAnswer.countDown();
	}

	private static HttpServer start() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/providers", exchange -> {
				called.countDown();
				try {
					mayAnswer.await(10, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				byte[] body = "{\"providerRef\":\"stub-auth\",\"outcome\":\"APPROVED\"}"
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
