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
	 * @param candidates         1단계(사진 → 후보) 생성기: fake | gemini
	 * @param sprite             2단계(후보 → 걷기 시트) 생성기: fake (PixelLab 예정)
	 * @param staleAfter         RUNNING 이 이 시간을 넘으면 서버가 꺼져 멈춘 작업으로 본다
	 * @param staleCheckInterval 멈춘 작업을 확인하는 간격
	 */
	public record Generation(String candidates, String sprite, Duration workerDelay,
			Duration staleAfter, Duration staleCheckInterval, Gemini gemini) {
	}

	/**
	 * @param apiRevision Gemini interactions API 버전 헤더. 응답 형식이 이 날짜 기준으로 고정된다
	 */
	public record Gemini(String apiKey, String model, String apiRevision, Duration timeout) {
	}
}
