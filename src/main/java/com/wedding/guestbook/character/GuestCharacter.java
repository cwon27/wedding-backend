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

	@PrePersist
	void onCreate() {
		createdAt = updatedAt = OffsetDateTime.now();
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = OffsetDateTime.now();
	}
}
