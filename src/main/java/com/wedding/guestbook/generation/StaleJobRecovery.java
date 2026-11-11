package com.wedding.guestbook.generation;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.wedding.guestbook.character.CharacterGenerationProcessor;
import com.wedding.guestbook.config.AppProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 멈춘 작업 복구.
 *
 * <p>워커는 작업을 RUNNING 으로 바꾼 뒤 AI 를 호출한다. 그 사이 서버가 꺼지면(배포, 재시작, 장애)
 * 작업이 RUNNING 으로 남고, 워커는 QUEUED 만 꺼내므로 아무도 다시 처리하지 않는다.
 * 하객 화면은 "생성 중" 에서 영원히 멈춘다.
 *
 * <p>그래서 서버가 켜질 때 한 번, 그 뒤 주기적으로 "시작한 지 stale-after 가 넘었는데 아직 RUNNING" 인 작업을 찾아
 * 실패 1회로 처리한다. 시도 횟수가 남았으면 다시 대기열로, 다 썼으면 FAILED.
 *
 * <p>stale-after 는 정상 처리 시간보다 충분히 길어야 한다. 짧으면 아직 처리 중인 작업을 다시 대기열에 넣어
 * 같은 작업이 두 번 처리된다 (AI 비용도 두 번).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleJobRecovery {

	private final GenerationJobRepository jobs;
	private final CharacterGenerationProcessor processor;
	private final AppProperties props;
	private final TransactionTemplate tx;

	/** 첫 실행은 서버 시작 직후(initialDelay 0), 그 뒤로는 이전 실행이 끝나고 stale-check-interval 마다 */
	@Scheduled(fixedDelayString = "${app.generation.stale-check-interval}")
	public void recover() {
		OffsetDateTime startedBefore = OffsetDateTime.now().minus(props.generation().staleAfter());

		List<GenerationJob> gaveUp = tx.execute(status -> {
			List<GenerationJob> stale = jobs.lockStaleRunning(startedBefore);
			for (GenerationJob job : stale) {
				job.recoverStale();
				log.warn("멈춘 작업 복구 job={} type={} character={} attempts={} → {}",
						job.getId(), job.getType(), job.getCharacterId(), job.getAttempts(), job.getStatus());
			}
			return stale.stream().filter(GenerationJob::isFailed).toList();
		});

		// 재시도 횟수를 다 쓴 작업은 캐릭터도 실패 처리해서 하객 화면이 "실패" 를 보여주게 한다
		for (GenerationJob job : gaveUp) {
			processor.markFailed(job.getCharacterId());
		}
	}
}
