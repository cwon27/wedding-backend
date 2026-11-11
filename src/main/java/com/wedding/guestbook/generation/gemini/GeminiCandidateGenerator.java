package com.wedding.guestbook.generation.gemini;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.wedding.guestbook.config.AppProperties;
import com.wedding.guestbook.generation.CandidateGenerator;

/**
 * Gemini(Nano Banana) 로 하객 사진 → 정면 픽셀 캐릭터 후보 4장.
 *
 * <p>API: POST /v1beta/interactions — 텍스트 프롬프트와 사진을 input 배열로 보내면
 * 응답의 steps[].content[] 에 base64 이미지가 담겨 온다.
 *
 * <p>한 번 호출에 이미지 1장이 나오므로 4번 호출한다. 순서대로 부르면 4배 오래 걸려서
 * executor 로 동시에 보낸다 (운영: 가상 스레드, 테스트: 순서대로 실행).
 */
public class GeminiCandidateGenerator implements CandidateGenerator {

	/**
	 * 배경을 마젠타(#FF00FF)로 요청한다. 옷이나 머리색에 거의 안 쓰이는 색이라
	 * 후처리에서 배경만 지우기 쉽다 (도트 게임 스프라이트에서 흔히 쓰는 방법).
	 */
	static final String PROMPT = """
			Turn the person in this photo into ONE cute chibi pixel art game character.
			- Full body, standing upright, facing the viewer (front view), arms relaxed.
			- 16-bit RPG sprite style like Stardew Valley: big head, small body (about 2.5 heads tall).
			- Keep what makes them recognizable: hairstyle and hair color, glasses or accessories,
			  skin tone, and the colors and style of their outfit.
			- Crisp pixel art with clean dark outlines and a limited color palette. No anti-aliasing, no blur.
			- Center the character with empty space around it.
			- Background: plain solid magenta (#FF00FF) only. No shadow, no floor, no text, no other objects.
			""";

	private final RestClient http;
	private final AppProperties.Gemini props;
	private final Executor executor;

	/**
	 * @param http 기본 주소(https://generativelanguage.googleapis.com)와 타임아웃이 설정된 클라이언트
	 */
	public GeminiCandidateGenerator(RestClient http, AppProperties.Gemini props, Executor executor) {
		if (props.apiKey() == null || props.apiKey().isBlank()) {
			throw new IllegalStateException("app.generation.candidates=gemini 이면 GEMINI_API_KEY 가 필요합니다");
		}
		this.http = http;
		this.props = props;
		this.executor = executor;
	}

	@Override
	public List<byte[]> generateCandidates(byte[] photo, String photoMimeType) {
		String photoBase64 = Base64.getEncoder().encodeToString(photo);
		List<CompletableFuture<byte[]>> calls = IntStream.range(0, CANDIDATE_COUNT)
				.mapToObj(i -> CompletableFuture.supplyAsync(() -> generateOne(photoBase64, photoMimeType), executor))
				.toList();
		try {
			return calls.stream().map(CompletableFuture::join).toList();
		}
		catch (CompletionException e) {
			// 하나라도 실패하면 작업 전체를 실패로 처리 → 워커가 재시도한다
			throw e.getCause() instanceof RuntimeException cause ? cause : e;
		}
	}

	private byte[] generateOne(String photoBase64, String photoMimeType) {
		Map<String, Object> body = Map.of(
				"model", props.model(),
				"input", List.of(
						Map.of("type", "text", "text", PROMPT),
						Map.of("type", "image", "mime_type", photoMimeType, "data", photoBase64)),
				"response_format", Map.of(
						"type", "image",
						"mime_type", "image/png", // JPEG 는 압축 얼룩이 생겨 배경 제거가 어려워진다
						"aspect_ratio", "1:1",
						"image_size", "1K"));

		InteractionResponse response = http.post()
				.uri("/v1beta/interactions")
				.header("x-goog-api-key", props.apiKey())
				.header("Api-Revision", props.apiRevision())
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(InteractionResponse.class);

		return Base64.getDecoder().decode(lastImage(response));
	}

	/** 응답에서 마지막 이미지 블록을 찾는다. 안전 정책 등으로 이미지가 없으면 예외 */
	private static String lastImage(InteractionResponse response) {
		String found = null;
		if (response != null && response.steps() != null) {
			for (InteractionResponse.Step step : response.steps()) {
				if (step.content() == null) {
					continue;
				}
				for (InteractionResponse.Content content : step.content()) {
					if ("image".equals(content.type()) && content.data() != null) {
						found = content.data();
					}
				}
			}
		}
		if (found == null) {
			throw new IllegalStateException("Gemini 응답에 이미지가 없습니다 (안전 정책으로 거절됐을 수 있음)");
		}
		return found;
	}
}
