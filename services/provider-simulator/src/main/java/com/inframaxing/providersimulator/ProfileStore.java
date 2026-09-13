package com.inframaxing.providersimulator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ProfileStore {

	private final Map<String, BehaviourProfile> profiles = new ConcurrentHashMap<>();

	private final BehaviourProfile fallback;

	public ProfileStore(SimulatorSettings settings) {
		this.fallback = new BehaviourProfile(settings.latency().toMillis(), 0d, 0d);
	}

	public BehaviourProfile of(String providerCode) {
		return profiles.getOrDefault(providerCode, fallback);
	}

	public BehaviourProfile set(String providerCode, BehaviourProfile profile) {
		profiles.put(providerCode, profile);
		return profile;
	}
}
