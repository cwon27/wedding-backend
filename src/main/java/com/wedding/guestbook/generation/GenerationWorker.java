package com.wedding.guestbook.generation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.wedding.guestbook.character.CharacterGenerationProcessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대기열 워커. 일정 간격으로 대기 작업을 하나씩 꺼내 이미지를 생성한다.
 * 작업을 꺼내는 트랜잭션과 생성(오래 걸림)을 분리해서, AI 호출 중에 DB 잠금을 잡고 있지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationWorker {

	private static final int MAX_ATTEMPTS = 3;

	private final GenerationJobRepository jobs;
	private final CharacterGenerationProcessor processor;
	private final TransactionTemplate tx;

	@Scheduled(fixedDelayString = "${app.generation.worker-delay}")
	public void poll() {
		GenerationJob job = tx.execute(status -> jobs.lockNextQueued().map(j -> {
			j.start();
			return j;
		}).orElse(null));
		if (job == null) {
			return;
		}

		try {
			switch (job.getType()) {
				case CANDIDATES -> processor.generateCandidates(job.getCharacterId());
				case SPRITE -> processor.generateSpriteSheet(job.getCharacterId());
			}
			tx.executeWithoutResult(status -> jobs.findById(job.getId()).ifPresent(GenerationJob::finish));
			log.info("생성 완료 job={} type={} character={}", job.getId(), job.getType(), job.getCharacterId());
		}
		catch (RuntimeException e) {
			log.warn("생성 작업 실패 job={} attempt={}", job.getId(), job.getAttempts(), e);
			boolean gaveUp = Boolean.TRUE.equals(tx.execute(status -> jobs.findById(job.getId()).map(j -> {
				j.fail(e.getMessage(), MAX_ATTEMPTS);
				return j.getStatus() == GenerationJob.Status.FAILED;
			}).orElse(false)));
			if (gaveUp) {
				processor.markFailed(job.getCharacterId());
			}
		}
	}
}
