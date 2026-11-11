package com.wedding.guestbook.generation.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.wedding.guestbook.config.AppProperties;

/**
 * 실제 Gemini 를 부르지 않고(비용 0) 가짜 서버(MockRestServiceServer)로
 * "우리가 문서대로 요청을 보내는지"와 "응답에서 이미지를 제대로 꺼내는지"를 확인한다.
 */
class GeminiCandidateGeneratorTest {

	private static final String URL = "https://generativelanguage.googleapis.com/v1beta/interactions";
	private static final AppProperties.Gemini PROPS =
			new AppProperties.Gemini("test-key", "gemini-nano-banana-2.1", "2026-05-20", Duration.ofSeconds(5));
	private static final byte[] PHOTO = { 1, 2, 3 };

	private MockRestServiceServer server;
	private GeminiCandidateGenerator generator;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
		server = MockRestServiceServer.bindTo(builder).build();
		// 테스트에서는 동시에 보내지 않고 순서대로 실행(Runnable::run)해서 결과를 예측 가능하게 한다
		generator = new GeminiCandidateGenerator(builder.build(), PROPS, Runnable::run);
	}

	@Test
	void 사진과_프롬프트를_보내고_응답_이미지_4장을_꺼낸다() {
		String imageBase64 = Base64.getEncoder().encodeToString(new byte[] { 9, 9, 9 });
		server.expect(times(4), requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("x-goog-api-key", "test-key"))
				.andExpect(header("Api-Revision", "2026-05-20"))
				.andExpect(jsonPath("$.model").value("gemini-nano-banana-2.1"))
				.andExpect(jsonPath("$.input[0].type").value("text"))
				.andExpect(jsonPath("$.input[1].type").value("image"))
				.andExpect(jsonPath("$.input[1].mime_type").value("image/jpeg"))
				.andExpect(jsonPath("$.input[1].data").value(Base64.getEncoder().encodeToString(PHOTO)))
				.andExpect(jsonPath("$.response_format.type").value("image"))
				.andRespond(withSuccess("""
						{ "id": "abc", "steps": [
						    { "type": "user_input" },
						    { "type": "model_output",
						      "content": [ { "type": "image", "mime_type": "image/png", "data": "%s" } ] } ] }
						""".formatted(imageBase64), MediaType.APPLICATION_JSON));

		List<byte[]> candidates = generator.generateCandidates(PHOTO, "image/jpeg");

		assertThat(candidates).hasSize(4).allSatisfy(bytes -> assertThat(bytes).containsExactly(9, 9, 9));
		server.verify();
	}

	@Test
	void 응답에_이미지가_없으면_실패한다() {
		server.expect(requestTo(URL)).andRespond(withSuccess("""
				{ "steps": [ { "type": "model_output", "content": [ { "type": "text", "text": "I can't help with that." } ] } ] }
				""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> generator.generateCandidates(PHOTO, "image/jpeg"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("이미지가 없습니다");
	}

	@Test
	void API_오류는_그대로_올려서_워커가_재시도하게_한다() {
		// 예: 크레딧 미충전, 요청 한도 초과
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThatThrownBy(() -> generator.generateCandidates(PHOTO, "image/jpeg"))
				.isInstanceOf(HttpClientErrorException.class);
	}

	@Test
	void API_키가_없으면_서버_시작부터_막는다() {
		AppProperties.Gemini noKey = new AppProperties.Gemini("", "m", "r", Duration.ofSeconds(1));

		assertThatThrownBy(() -> new GeminiCandidateGenerator(RestClient.create(), noKey, Runnable::run))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("GEMINI_API_KEY");
	}
}
