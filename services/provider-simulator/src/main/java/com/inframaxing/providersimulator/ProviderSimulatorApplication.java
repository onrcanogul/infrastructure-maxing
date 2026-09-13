package com.inframaxing.providersimulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * A stand-in for the payment provider.
 *
 * <p>Its whole job is to be someone else's network call: to answer after a delay we choose, so
 * payment-service can be measured against a dependency whose speed it does not control. It has no
 * database and no layers on purpose - this is a test instrument, not an example of architecture.
 */
@SpringBootApplication
@EnableConfigurationProperties(SimulatorSettings.class)
public class ProviderSimulatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProviderSimulatorApplication.class, args);
	}

}
