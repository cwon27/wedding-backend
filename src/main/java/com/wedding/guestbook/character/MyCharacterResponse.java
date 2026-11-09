package com.wedding.guestbook.character;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.wedding.guestbook.storage.StorageService;

/** 캐릭터 만들기 화면용: 내 캐릭터의 진행 상태. 프론트 @wedding/api 의 MyCharacter 와 같은 모양 */
public record MyCharacterResponse(
		UUID id,
		String name,
		String dialogue,
		String status,
		List<Candidate> candidates,
		int regenerationsLeft,
		Long queuePosition,
		String imageUrl,
		CharacterResponse.Sprite sprite) {

	public record Candidate(long id, String url) {
	}

	public static MyCharacterResponse of(GuestCharacter c, CharacterService service, StorageService storage) {
		CharacterResponse base = CharacterResponse.of(c, storage);
		return new MyCharacterResponse(
				c.getId(),
				c.getName(),
				c.getDialogue(),
				c.getStatus().name().toLowerCase(Locale.ROOT),
				service.currentCandidates(c).stream()
						.map(cand -> new Candidate(cand.getId(), storage.publicUrl(cand.getImageKey())))
						.toList(),
				service.regenerationsLeft(c),
				service.queuePosition(c),
				base.imageUrl(),
				base.sprite());
	}
}
