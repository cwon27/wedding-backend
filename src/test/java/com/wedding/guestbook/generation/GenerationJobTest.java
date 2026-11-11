package com.wedding.guestbook.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** 작업 상태 전이 규칙: 실패/멈춤 복구 시 재시도 횟수가 남았으면 다시 대기열, 다 썼으면 FAILED */
class GenerationJobTest {

	private final GenerationJob job = GenerationJob.queue(UUID.randomUUID(), GenerationJob.Type.CANDIDATES);

	@Test
	void 멈춘_작업은_시도가_남았으면_다시_대기열로() {
		job.start(); // 1번째 시도 중 서버가 꺼졌다고 가정

		job.recoverStale();

		assertThat(job.getStatus()).isEqualTo(GenerationJob.Status.QUEUED);
		assertThat(job.getAttempts()).isEqualTo(1);
		assertThat(job.getError()).contains("서버가 중단");
	}

	@Test
	void 계속_멈추는_작업도_최대_시도_후에는_FAILED() {
		for (int i = 0; i < GenerationJob.MAX_ATTEMPTS; i++) {
			job.start();
			job.recoverStale();
		}

		assertThat(job.isFailed()).isTrue();
		assertThat(job.getAttempts()).isEqualTo(GenerationJob.MAX_ATTEMPTS);
	}

	@Test
	void 일반_실패와_멈춤_복구는_같은_재시도_규칙을_쓴다() {
		job.start();
		job.fail("API 오류");
		job.start();
		job.recoverStale();
		job.start();
		job.fail("API 오류");

		assertThat(job.isFailed()).isTrue();
	}
}
