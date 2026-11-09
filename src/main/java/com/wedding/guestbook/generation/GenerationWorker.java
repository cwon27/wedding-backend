package com.wedding.guestbook.generation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

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
	private final CharacterImageGenerator generator;
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
			process(job);
			tx.executeWithoutResult(status -> jobs.findById(job.getId()).ifPresent(GenerationJob::finish));
		}
		catch (RuntimeException e) {
			log.warn("생성 작업 실패 job={} attempt={}", job.getId(), job.getAttempts(), e);
			tx.executeWithoutResult(status -> jobs.findById(job.getId())
					.ifPresent(j -> j.fail(e.getMessage(), MAX_ATTEMPTS)));
		}
	}

	private void process(GenerationJob job) {
		// TODO: 작업 종류별 처리
		//  CANDIDATES: 원본 사진 읽기 → generator.generateCandidates → 후처리(64px, 배경 제거)
		//              → 저장소에 저장 → character_candidate 저장 → 캐릭터 상태 CHOOSING
		//  SPRITE:     선택한 이미지 → generator.generateSpriteSheet → 후처리(프레임 정렬)
		//              → 저장 → 캐릭터 상태 COMPLETE
		log.info("생성 작업 처리 job={} type={} character={} (generator={})",
				job.getId(), job.getType(), job.getCharacterId(), generator.getClass().getSimpleName());
	}
}
