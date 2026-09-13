package com.inframaxing.providersimulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SimulatorSettings.class)
public class ProviderSimulatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProviderSimulatorApplication.class, args);
	}

}
