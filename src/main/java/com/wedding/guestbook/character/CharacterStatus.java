package com.wedding.guestbook.character;

/** 캐릭터 생성 단계. 프론트 @wedding/api 의 CharacterStatus 와 같은 값(소문자)으로 내려간다 */
public enum CharacterStatus {
	PENDING_CANDIDATES, // 후보 4개 생성 대기/진행 중
	CHOOSING, //           하객이 후보를 고르는 중
	PENDING_SPRITE, //     걷기 스프라이트시트 생성 대기/진행 중
	COMPLETE,
	FAILED
}
