package com.wedding.guestbook.character;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestCharacterRepository extends JpaRepository<GuestCharacter, UUID> {

	List<GuestCharacter> findByGuestbookIdOrderByUpdatedAtAsc(Long guestbookId);

	/** since 와 같은 시각도 포함한다 (프론트는 id 로 중복을 덮어쓴다) */
	List<GuestCharacter> findByGuestbookIdAndUpdatedAtGreaterThanEqualOrderByUpdatedAtAsc(
			Long guestbookId, OffsetDateTime since);
}
