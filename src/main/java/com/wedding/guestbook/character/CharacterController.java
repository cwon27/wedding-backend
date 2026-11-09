package com.wedding.guestbook.character;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.wedding.guestbook.guestbook.Guestbook;
import com.wedding.guestbook.guestbook.GuestbookRepository;
import com.wedding.guestbook.storage.StorageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
public class CharacterController {

	private final GuestbookRepository guestbooks;
	private final GuestCharacterRepository characters;
	private final StorageService storage;

	/**
	 * 하객 화면 폴링. since 가 없으면 전체, 있으면 그 이후 바뀐 캐릭터만 내려준다.
	 * 숨김 처리된 캐릭터도 내려줘야 프론트가 화면에서 지울 수 있다 (TODO: hidden 필드 추가)
	 */
	@GetMapping
	public CharacterResponse.SyncResponse list(@RequestParam(required = false) OffsetDateTime since) {
		Guestbook guestbook = guestbooks.findFirstByOrderByIdAsc()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "방명록이 아직 없습니다"));

		List<GuestCharacter> changed = since == null
				? characters.findByGuestbookIdOrderByUpdatedAtAsc(guestbook.getId())
				: characters.findByGuestbookIdAndUpdatedAtGreaterThanEqualOrderByUpdatedAtAsc(guestbook.getId(), since);

		OffsetDateTime cursor = changed.isEmpty() ? since : changed.getLast().getUpdatedAt();
		return new CharacterResponse.SyncResponse(
				changed.stream().map(c -> CharacterResponse.of(c, storage)).toList(),
				cursor == null ? null : cursor.toString());
	}

	// TODO: POST /api/characters            사진 + 이름 + 대사 업로드 → 후보 생성 작업 등록 (기기당 1개)
	// TODO: POST /api/characters/me/regenerate  후보 재생성 (기기당 1회)
	// TODO: POST /api/characters/me/choose      후보 선택 → 스프라이트 생성 작업 등록
	// TODO: GET  /api/characters/me             내 캐릭터 진행 상태
}
