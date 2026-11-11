package com.wedding.guestbook.generation.gemini;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Gemini interactions API 응답 중 우리가 쓰는 부분만.
 * <pre>
 * { "steps": [ { "type": "model_output",
 *                "content": [ { "type": "image", "mime_type": "image/png", "data": "base64..." } ] } ] }
 * </pre>
 * 모르는 필드는 무시해서, 응답에 필드가 추가돼도 깨지지 않게 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record InteractionResponse(List<Step> steps) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Step(String type, List<Content> content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Content(String type, String data) {
	}
}
