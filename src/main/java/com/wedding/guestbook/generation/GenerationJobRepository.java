package com.wedding.guestbook.generation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

	/**
	 * 가장 오래된 대기 작업 1건을 잠그고 가져온다.
	 * SKIP LOCKED: 워커가 여러 개(서버 여러 대)여도 같은 작업을 두 번 가져가지 않는다.
	 */
	@Query(value = """
			select * from generation_job
			where status = 'QUEUED'
			order by created_at
			limit 1
			for update skip locked
			""", nativeQuery = true)
	Optional<GenerationJob> lockNextQueued();

	Optional<GenerationJob> findFirstByCharacterIdOrderByIdDesc(UUID characterId);

	/** 하객에게 "대기 n번째" 를 보여주기 위한 앞선 작업 수 */
	@Query("select count(j) from GenerationJob j where j.status = 'QUEUED' and j.id < :jobId")
	long countQueuedBefore(Long jobId);
}
