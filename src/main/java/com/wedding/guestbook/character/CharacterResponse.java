package com.wedding.guestbook.character;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.wedding.guestbook.storage.StorageService;

/** 프론트 @wedding/api 의 Character 타입과 같은 모양 */
public record CharacterResponse(
		UUID id,
		String name,
		String dialogue,
		String status,
		String imageUrl,
		Sprite sprite,
		OffsetDateTime createdAt) {

	public record Sprite(String url, int columns, int rows) {
	}

	public record SyncResponse(List<CharacterResponse> characters, String syncCursor) {
	}

	public static CharacterResponse of(GuestCharacter c, StorageService storage) {
		Sprite sprite = c.getSpriteKey() == null ? null
				: new Sprite(storage.publicUrl(c.getSpriteKey()), c.getSpriteColumns(), c.getSpriteRows());
		return new CharacterResponse(
				c.getId(),
				c.getName(),
				c.getDialogue(),
				c.getStatus().name().toLowerCase(Locale.ROOT),
				c.getImageKey() == null ? null : storage.publicUrl(c.getImageKey()),
				sprite,
				c.getCreatedAt());
	}
}
