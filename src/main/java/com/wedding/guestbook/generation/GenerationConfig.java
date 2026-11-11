package com.wedding.guestbook.generation;

import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.wedding.guestbook.config.AppProperties;
import com.wedding.guestbook.generation.gemini.GeminiCandidateGenerator;

/**
 * 단계별 생성기 고르기. .env 의 CANDIDATE_GENERATOR / SPRITE_GENERATOR 값만 바꾸면 된다.
 * 나머지 코드(워커, 후처리, 저장)는 CandidateGenerator / SpriteSheetGenerator 인터페이스만 안다.
 */
@Configuration
public class GenerationConfig {

	private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com";

	@Bean
	CandidateGenerator candidateGenerator(AppProperties props, RestClient.Builder restClientBuilder) {
		return switch (props.generation().candidates()) {
			case "fake" -> new FakeImageGenerator();
			case "gemini" -> {
				AppProperties.Gemini gemini = props.generation().gemini();
				JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
				requestFactory.setReadTimeout(gemini.timeout()); // 이미지 생성은 수십 초 걸릴 수 있다
				RestClient http = restClientBuilder.baseUrl(GEMINI_BASE_URL).requestFactory(requestFactory).build();
				// 가상 스레드: 응답을 기다리는 동안 OS 스레드를 붙잡지 않아서 동시 호출에 적합하다
				yield new GeminiCandidateGenerator(http, gemini, Executors.newVirtualThreadPerTaskExecutor());
			}
			default -> throw new IllegalStateException("알 수 없는 후보 생성기: " + props.generation().candidates());
		};
	}

	@Bean
	SpriteSheetGenerator spriteSheetGenerator(AppProperties props) {
		return switch (props.generation().sprite()) {
			case "fake" -> new FakeImageGenerator();
			default -> throw new IllegalStateException("알 수 없는 스프라이트 생성기: " + props.generation().sprite());
		};
	}
}
