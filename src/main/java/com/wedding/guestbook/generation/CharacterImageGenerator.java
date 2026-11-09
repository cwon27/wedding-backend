package com.wedding.guestbook.generation;

import java.util.List;

/**
 * 픽셀 캐릭터 이미지 생성기.
 * 구현체는 app.generation.generator 설정으로 고른다: fake | api | comfyui
 * 나머지 코드(대기열 워커, 후처리, 저장)는 이 인터페이스만 알고, 어떤 AI 를 쓰는지 모른다.
 */
public interface CharacterImageGenerator {

	int CANDIDATE_COUNT = 4;

	/** 하객 사진 → 픽셀 캐릭터 후보 CANDIDATE_COUNT 장 (PNG) */
	List<byte[]> generateCandidates(byte[] photo);

	/** 선택한 캐릭터 → 4열(걷기 프레임) x 3행(앞/옆/뒤) 스프라이트시트 1장 (PNG) */
	byte[] generateSpriteSheet(byte[] character);
}
