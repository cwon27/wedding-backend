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

	/** 작업 하나를 최대 몇 번 시도할지. 워커의 실패 처리와 멈춘 작업 복구가 같은 규칙을 쓴다 */
	static final int MAX_ATTEMPTS = 3;

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

	void fail(String message) {
		error = message;
		status = attempts < MAX_ATTEMPTS ? Status.QUEUED : Status.FAILED; // 횟수가 남았으면 다시 대기열로
		finishedAt = OffsetDateTime.now();
	}

	/**
	 * 처리 도중 서버가 꺼져 RUNNING 으로 남은 작업. 실패 1회로 보고 fail 과 같은 규칙을 적용한다.
	 * (start() 에서 이미 시도 횟수를 올렸으므로, 계속 꺼지는 작업도 결국 FAILED 로 끝난다)
	 */
	void recoverStale() {
		fail("처리 중 서버가 중단되어 다시 시도합니다");
	}

	boolean isFailed() {
		return status == Status.FAILED;
	}
}
