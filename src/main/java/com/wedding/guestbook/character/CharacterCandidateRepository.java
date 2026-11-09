package com.wedding.guestbook.character;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CharacterCandidateRepository extends JpaRepository<CharacterCandidate, Long> {

	List<CharacterCandidate> findByCharacterIdAndRoundOrderBySlot(UUID characterId, short round);

	/** 생성 작업을 재시도할 때 앞서 일부만 저장된 후보를 지운다 */
	@Modifying
	@Query("delete from CharacterCandidate c where c.characterId = :characterId and c.round = :round")
	void deleteRound(UUID characterId, short round);
}
