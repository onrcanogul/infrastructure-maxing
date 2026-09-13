package com.inframaxing.providersimulator;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AdminController {

	private final ProfileStore profiles;

	public AdminController(ProfileStore profiles) {
		this.profiles = profiles;
	}

	@PostMapping("/admin/{code}/profile")
	public BehaviourProfile set(@PathVariable String code, @RequestBody BehaviourProfile profile) {
		validate(profile);
		return profiles.set(code, profile);
	}

	@GetMapping("/admin/{code}/profile")
	public BehaviourProfile get(@PathVariable String code) {
		return profiles.of(code);
	}

	private static void validate(BehaviourProfile profile) {
		if (profile.latencyMs() < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latencyMs must not be negative");
		}
		if (isNotARate(profile.errorRate())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "errorRate must be between 0 and 1");
		}
		if (isNotARate(profile.timeoutRate())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timeoutRate must be between 0 and 1");
		}
		if (profile.errorRate() + profile.timeoutRate() > 1d) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"errorRate + timeoutRate must not exceed 1");
		}
	}

	private static boolean isNotARate(double value) {
		return Double.isNaN(value) || value < 0d || value > 1d;
	}
}
