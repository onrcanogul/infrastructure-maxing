package com.inframaxing.paymentservice.provider;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class ProviderClientConfig {

	@Bean
	RestClient paymentProviderRestClient(
			@Value("${app.provider.base-url}") String baseUrl,
			@Value("${app.provider.connect-timeout}") Duration connectTimeout,
			@Value("${app.provider.read-timeout}") Duration readTimeout) {
		HttpClient http = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(connectTimeout)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.build();
	}
}
