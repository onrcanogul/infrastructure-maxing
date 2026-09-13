package com.inframaxing.providersimulator;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the simulated provider behaves. Everything is configuration, so a run can be changed without
 * a rebuild - the same rule the rest of the repo follows.
 *
 * @param latency how long to wait before answering; this is the number the whole lab turns on
 * @param approve whether authorizations are approved or declined
 */
@ConfigurationProperties(prefix = "app.simulator")
public record SimulatorSettings(Duration latency, boolean approve) {
}
