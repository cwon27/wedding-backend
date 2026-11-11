package com.wedding.guestbook.generation;

/**
 * 2단계: 하객이 고른 캐릭터 → 걷기 스프라이트시트.
 * 구현체는 app.generation.sprite 설정으로 고른다 (GenerationConfig 참고).
 */
public interface SpriteSheetGenerator {

	/** 선택한 캐릭터(PNG) → 4열(걷기 프레임) x 3행(앞/옆/뒤) 스프라이트시트 1장 (PNG) */
	byte[] generateSpriteSheet(byte[] character);
}
