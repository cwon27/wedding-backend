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

	/**
	 * @param candidates 1단계(사진 → 후보) 생성기: fake | gemini
	 * @param sprite     2단계(후보 → 걷기 시트) 생성기: fake (PixelLab 예정)
	 */
	public record Generation(String candidates, String sprite, Duration workerDelay, Gemini gemini) {
	}

	/**
	 * @param apiRevision Gemini interactions API 버전 헤더. 응답 형식이 이 날짜 기준으로 고정된다
	 */
	public record Gemini(String apiKey, String model, String apiRevision, Duration timeout) {
	}
}
