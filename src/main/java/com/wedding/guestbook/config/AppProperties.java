package com.wedding.guestbook.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 app.* 설정 */
@ConfigurationProperties("app")
public record AppProperties(
		Cors cors,
		Device device,
		Guestbook guestbook,
		Generation generation) {

	public record Cors(List<String> allowedOrigins) {
	}

	public record Device(String cookieName, Duration maxAge, boolean secure) {
	}

	public record Guestbook(int maxRegenerations) {
	}

	public record Generation(String generator, Duration workerDelay) {
	}
}
