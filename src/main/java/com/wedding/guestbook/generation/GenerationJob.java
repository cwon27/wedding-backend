package com.wedding.guestbook.generation;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이미지 생성 대기열의 작업 1건 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GenerationJob {

	public enum Type {
		CANDIDATES, SPRITE
	}

	public enum Status {
		QUEUED, RUNNING, DONE, FAILED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID characterId;

	@Enumerated(EnumType.STRING)
	private Type type;

	@Enumerated(EnumType.STRING)
	private Status status;

	private int attempts;

	private String error;

	@Column(insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	private OffsetDateTime startedAt;

	private OffsetDateTime finishedAt;

	public static GenerationJob queue(UUID characterId, Type type) {
		GenerationJob job = new GenerationJob();
		job.characterId = characterId;
		job.type = type;
		job.status = Status.QUEUED;
		return job;
	}

	void start() {
		status = Status.RUNNING;
		attempts++;
		startedAt = OffsetDateTime.now();
	}

	void finish() {
		status = Status.DONE;
		finishedAt = OffsetDateTime.now();
	}

	void fail(String message, int maxAttempts) {
		error = message;
		status = attempts < maxAttempts ? Status.QUEUED : Status.FAILED; // 횟수가 남았으면 다시 대기열로
		finishedAt = OffsetDateTime.now();
	}
}
