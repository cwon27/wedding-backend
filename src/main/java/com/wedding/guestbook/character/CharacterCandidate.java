package com.wedding.guestbook.character;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 후보 이미지 1장. round 0 = 최초 4장, round 1 = 재생성 4장 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterCandidate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID characterId;

	private short round;

	private short slot;

	private String imageKey;

	@Column(insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	public CharacterCandidate(UUID characterId, int round, int slot, String imageKey) {
		this.characterId = characterId;
		this.round = (short) round;
		this.slot = (short) slot;
		this.imageKey = imageKey;
	}
}
