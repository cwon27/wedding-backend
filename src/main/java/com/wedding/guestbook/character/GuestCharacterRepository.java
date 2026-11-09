package com.wedding.guestbook.character;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GuestCharacterRepository extends JpaRepository<GuestCharacter, UUID> {

	List<GuestCharacter> findByGuestbookIdOrderByUpdatedAtAsc(Long guestbookId);

	/** since 와 같은 시각도 포함한다 (프론트는 id 로 중복을 덮어쓴다) */
	List<GuestCharacter> findByGuestbookIdAndUpdatedAtGreaterThanEqualOrderByUpdatedAtAsc(
			Long guestbookId, OffsetDateTime since);

	Optional<GuestCharacter> findByGuestbookIdAndDeviceId(Long guestbookId, String deviceId);

	long countByGuestbookId(Long guestbookId);

	/**
	 * 재생성 시작. 조건(선택 중 + 횟수 남음)을 UPDATE 의 WHERE 에 넣어서,
	 * 버튼을 연타해 요청이 동시에 와도 DB 가 한 번만 허용한다. 반환값 0 = 거절.
	 */
	@Modifying(clearAutomatically = true)
	@Query("""
			update GuestCharacter c
			set c.regenCount = c.regenCount + 1, c.status = :to, c.updatedAt = :now
			where c.id = :id and c.status = :from and c.regenCount < :maxRegenerations
			""")
	int startRegeneration(UUID id, int maxRegenerations, CharacterStatus from, CharacterStatus to, OffsetDateTime now);

	/** 후보 선택. 마찬가지로 "선택 중" 상태일 때 한 번만 성공한다 */
	@Modifying(clearAutomatically = true)
	@Query("""
			update GuestCharacter c
			set c.imageKey = :imageKey, c.status = :to, c.updatedAt = :now
			where c.id = :id and c.status = :from
			""")
	int choose(UUID id, String imageKey, CharacterStatus from, CharacterStatus to, OffsetDateTime now);
}
