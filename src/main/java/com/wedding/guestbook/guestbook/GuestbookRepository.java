package com.wedding.guestbook.guestbook;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestbookRepository extends JpaRepository<Guestbook, Long> {

	/** 이 사이트는 부부 1쌍 전용이라 방명록이 하나뿐이다 */
	Optional<Guestbook> findFirstByOrderByIdAsc();
}
