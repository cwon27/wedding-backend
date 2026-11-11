package com.wedding.guestbook.generation;

import java.util.List;

/**
 * 1단계: 하객 사진 → 닮은 정면 픽셀 캐릭터 후보.
 * 구현체는 app.generation.candidates 설정으로 고른다 (GenerationConfig 참고).
 */
public interface CandidateGenerator {

	int CANDIDATE_COUNT = 4;

	/**
	 * @param photo         하객 사진
	 * @param photoMimeType image/jpeg, image/png, image/webp
	 * @return 후보 이미지 CANDIDATE_COUNT 장. 크기와 배경은 생성기마다 달라도 되고, ImagePostProcessor 가 맞춘다
	 */
	List<byte[]> generateCandidates(byte[] photo, String photoMimeType);
}
