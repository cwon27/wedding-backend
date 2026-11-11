package com.wedding.guestbook.character;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.wedding.guestbook.generation.CandidateGenerator;
import com.wedding.guestbook.generation.ImagePostProcessor;
import com.wedding.guestbook.generation.SpriteSheetGenerator;
import com.wedding.guestbook.storage.StorageService;

import lombok.RequiredArgsConstructor;

/**
 * 대기열 워커가 꺼낸 작업을 실제로 처리한다.
 * 이미지 생성(오래 걸림)은 트랜잭션 밖에서 하고, 결과 저장만 짧은 트랜잭션으로 묶는다.
 */
@Component
@RequiredArgsConstructor
public class CharacterGenerationProcessor {

	private final GuestCharacterRepository characters;
	private final CharacterCandidateRepository candidates;
	private static final Map<String, String> MIME_TYPES = Map.of(
			"jpg", "image/jpeg", "png", "image/png", "webp", "image/webp");

	private final CandidateGenerator candidateGenerator;
	private final SpriteSheetGenerator spriteSheetGenerator;
	private final ImagePostProcessor postProcessor;
	private final StorageService storage;
	private final TransactionTemplate tx;

	/** 사진 → 후보 4장 저장 → 상태 CHOOSING */
	public void generateCandidates(UUID characterId) {
		GuestCharacter character = load(characterId);
		int round = character.currentRound();
		String photoKey = character.getPhotoKey();
		List<byte[]> images = candidateGenerator.generateCandidates(storage.get(photoKey), mimeTypeOf(photoKey));

		List<CharacterCandidate> saved = new ArrayList<>();
		for (int slot = 0; slot < images.size(); slot++) {
			String key = "characters/%s/candidates/%d-%d.png".formatted(characterId, round, slot);
			storage.put(key, postProcessor.candidate(images.get(slot)), "image/png");
			saved.add(new CharacterCandidate(characterId, round, slot, key));
		}

		tx.executeWithoutResult(status -> {
			candidates.deleteRound(characterId, (short) round);
			candidates.saveAll(saved);
			load(characterId).candidatesReady();
		});
	}

	/** 선택한 캐릭터 → 걷기 스프라이트시트 저장 → 상태 COMPLETE */
	public void generateSpriteSheet(UUID characterId) {
		GuestCharacter character = load(characterId);
		byte[] sheet = postProcessor.spriteSheet(spriteSheetGenerator.generateSpriteSheet(storage.get(character.getImageKey())));

		String key = "characters/%s/sprite.png".formatted(characterId);
		storage.put(key, sheet, "image/png");
		tx.executeWithoutResult(status -> load(characterId)
				.spriteReady(key, ImagePostProcessor.SPRITE_COLUMNS, ImagePostProcessor.SPRITE_ROWS));
	}

	/** 재시도 횟수를 다 쓴 경우 */
	public void markFailed(UUID characterId) {
		tx.executeWithoutResult(status -> characters.findById(characterId).ifPresent(GuestCharacter::generationFailed));
	}

	/** 저장할 때 붙인 확장자(characters/{id}/photo.jpg)로 사진 형식을 알아낸다 */
	private static String mimeTypeOf(String key) {
		String extension = key.substring(key.lastIndexOf('.') + 1);
		return MIME_TYPES.getOrDefault(extension, "image/jpeg");
	}

	private GuestCharacter load(UUID characterId) {
		return characters.findById(characterId)
				.orElseThrow(() -> new IllegalStateException("캐릭터가 없습니다: " + characterId));
	}
}
