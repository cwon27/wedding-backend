package com.wedding.guestbook.character;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "guest_character")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestCharacter {

	@Id
	private UUID id;

	private Long guestbookId;

	private String deviceId;

	private String name;

	private String dialogue;

	@Enumerated(EnumType.STRING)
	private CharacterStatus status;

	private int regenCount;

	private String photoKey;

	private String imageKey;

	private String spriteKey;

	private Short spriteColumns;

	private Short spriteRows;

	private boolean hidden;

	@Column(updatable = false)
	private OffsetDateTime createdAt;

	/** 폴링(since)의 기준. 상태가 바뀔 때마다 갱신된다 */
	private OffsetDateTime updatedAt;

	public static GuestCharacter create(Long guestbookId, String deviceId, String name, String dialogue) {
		GuestCharacter c = new GuestCharacter();
		c.id = UUID.randomUUID();
		c.guestbookId = guestbookId;
		c.deviceId = deviceId;
		c.name = name;
		c.dialogue = dialogue;
		c.status = CharacterStatus.PENDING_CANDIDATES;
		return c;
	}

	void attachPhoto(String key) {
		photoKey = key;
	}

	/** 현재 회차 후보 = 최초 0, 재생성 후 1 */
	public int currentRound() {
		return regenCount;
	}

	void candidatesReady() {
		status = CharacterStatus.CHOOSING;
	}

	void spriteReady(String key, int columns, int rows) {
		spriteKey = key;
		spriteColumns = (short) columns;
		spriteRows = (short) rows;
		status = CharacterStatus.COMPLETE;
	}

	void generationFailed() {
		status = CharacterStatus.FAILED;
	}

	@PrePersist
	void onCreate() {
		createdAt = updatedAt = OffsetDateTime.now();
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = OffsetDateTime.now();
	}
}
