package com.wedding.guestbook.character;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.wedding.guestbook.device.DeviceId;
import com.wedding.guestbook.storage.StorageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
public class CharacterController {

	private final CharacterService service;
	private final GuestCharacterRepository characters;
	private final StorageService storage;

	public record ChooseRequest(long candidateId) {
	}

	/**
	 * 하객 화면 폴링. since 가 없으면 전체, 있으면 그 이후 바뀐 캐릭터만 내려준다.
	 * 생성 중이거나 숨김 처리된 캐릭터도 내려서, 프론트가 상태를 보고 표시/제거한다.
	 */
	@GetMapping
	public CharacterResponse.SyncResponse list(@RequestParam(required = false) OffsetDateTime since) {
		Long guestbookId = service.guestbook().getId();
		List<GuestCharacter> changed = since == null
				? characters.findByGuestbookIdOrderByUpdatedAtAsc(guestbookId)
				: characters.findByGuestbookIdAndUpdatedAtGreaterThanEqualOrderByUpdatedAtAsc(guestbookId, since);

		OffsetDateTime cursor = changed.isEmpty() ? since : changed.getLast().getUpdatedAt();
		return new CharacterResponse.SyncResponse(
				changed.stream().map(c -> CharacterResponse.of(c, storage)).toList(),
				cursor == null ? null : cursor.toString());
	}

	/** 사진 + 이름 + 대사 → 후보 생성 대기열에 등록 (기기당 1개) */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public MyCharacterResponse create(@DeviceId String deviceId,
			@RequestParam String name,
			@RequestParam String dialogue,
			@RequestPart MultipartFile photo) {
		return MyCharacterResponse.of(service.create(deviceId, name, dialogue, photo), service, storage);
	}

	/** 내 캐릭터 진행 상태 (만들기 화면이 주기적으로 조회) */
	@GetMapping("/me")
	public MyCharacterResponse mine(@DeviceId String deviceId) {
		return MyCharacterResponse.of(service.mine(deviceId), service, storage);
	}

	/** 후보 다시 만들기 (기기당 1회) */
	@PostMapping("/me/regenerate")
	public MyCharacterResponse regenerate(@DeviceId String deviceId) {
		service.regenerate(deviceId);
		return mine(deviceId);
	}

	/** 후보 선택 → 걷기 스프라이트시트 생성 대기열에 등록 */
	@PostMapping("/me/choose")
	public MyCharacterResponse choose(@DeviceId String deviceId, @RequestBody ChooseRequest request) {
		service.choose(deviceId, request.candidateId());
		return mine(deviceId);
	}
}
