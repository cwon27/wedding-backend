package com.wedding.guestbook.character;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.wedding.guestbook.common.ApiException;
import com.wedding.guestbook.config.AppProperties;
import com.wedding.guestbook.generation.GenerationJob;
import com.wedding.guestbook.generation.GenerationJobRepository;
import com.wedding.guestbook.guestbook.Guestbook;
import com.wedding.guestbook.guestbook.GuestbookRepository;
import com.wedding.guestbook.storage.StorageService;

import lombok.RequiredArgsConstructor;

/**
 * 하객 캐릭터 만들기. 기기(쿠키)당 캐릭터 1개, 후보 재생성 1회 규칙을 여기서 지킨다.
 */
@Service
@RequiredArgsConstructor
public class CharacterService {

	private static final int MAX_NAME_LENGTH = 20;
	private static final int MAX_DIALOGUE_LENGTH = 100;
	private static final Map<String, String> PHOTO_EXTENSIONS = Map.of(
			"image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

	private final GuestbookRepository guestbooks;
	private final GuestCharacterRepository characters;
	private final CharacterCandidateRepository candidates;
	private final GenerationJobRepository jobs;
	private final StorageService storage;
	private final AppProperties props;

	public Guestbook guestbook() {
		return guestbooks.findFirstByOrderByIdAsc()
				.orElseThrow(() -> ApiException.notFound("NO_GUESTBOOK", "방명록이 아직 준비되지 않았어요."));
	}

	@Transactional
	public GuestCharacter create(String deviceId, String name, String dialogue, MultipartFile photo) {
		Guestbook guestbook = guestbook();
		if (guestbook.getStatus() == Guestbook.Status.READ_ONLY) {
			throw ApiException.conflict("GUESTBOOK_CLOSED", "방명록 작성이 마감됐어요.");
		}
		if (characters.findByGuestbookIdAndDeviceId(guestbook.getId(), deviceId).isPresent()) {
			throw ApiException.conflict("ALREADY_CREATED", "이 기기에서는 이미 캐릭터를 만들었어요.");
		}
		if (characters.countByGuestbookId(guestbook.getId()) >= guestbook.getMaxCharacters()) {
			throw ApiException.conflict("GUESTBOOK_FULL", "방명록 인원이 가득 찼어요.");
		}
		String extension = photoExtension(photo);

		GuestCharacter character = GuestCharacter.create(
				guestbook.getId(), deviceId, text(name, "이름", MAX_NAME_LENGTH), text(dialogue, "축하 메시지", MAX_DIALOGUE_LENGTH));
		String photoKey = "characters/%s/photo.%s".formatted(character.getId(), extension);
		storage.put(photoKey, bytes(photo), photo.getContentType());
		character.attachPhoto(photoKey);

		try {
			characters.saveAndFlush(character);
		}
		catch (DataIntegrityViolationException e) {
			// 같은 기기에서 거의 동시에 두 번 보낸 경우: (방명록, 기기) unique 제약이 막아준다
			throw ApiException.conflict("ALREADY_CREATED", "이 기기에서는 이미 캐릭터를 만들었어요.");
		}
		jobs.save(GenerationJob.queue(character.getId(), GenerationJob.Type.CANDIDATES));
		return character;
	}

	@Transactional(readOnly = true)
	public GuestCharacter mine(String deviceId) {
		return characters.findByGuestbookIdAndDeviceId(guestbook().getId(), deviceId)
				.orElseThrow(() -> ApiException.notFound("NO_CHARACTER", "아직 만든 캐릭터가 없어요."));
	}

	@Transactional
	public void regenerate(String deviceId) {
		GuestCharacter character = mine(deviceId);
		int max = props.guestbook().maxRegenerations();
		int updated = characters.startRegeneration(character.getId(), max,
				CharacterStatus.CHOOSING, CharacterStatus.PENDING_CANDIDATES, OffsetDateTime.now());
		if (updated == 0) {
			throw character.getStatus() != CharacterStatus.CHOOSING
					? ApiException.conflict("NOT_CHOOSING", "지금은 다시 만들 수 없어요.")
					: ApiException.conflict("NO_REGENERATIONS_LEFT", "다시 만들기는 %d번까지 할 수 있어요.".formatted(max));
		}
		jobs.save(GenerationJob.queue(character.getId(), GenerationJob.Type.CANDIDATES));
	}

	@Transactional
	public void choose(String deviceId, long candidateId) {
		GuestCharacter character = mine(deviceId);
		CharacterCandidate candidate = candidates.findById(candidateId)
				.filter(c -> c.getCharacterId().equals(character.getId()))
				.filter(c -> c.getRound() == character.currentRound())
				.orElseThrow(() -> ApiException.badRequest("INVALID_CANDIDATE", "선택한 캐릭터를 찾을 수 없어요."));

		int updated = characters.choose(character.getId(), candidate.getImageKey(),
				CharacterStatus.CHOOSING, CharacterStatus.PENDING_SPRITE, OffsetDateTime.now());
		if (updated == 0) {
			throw ApiException.conflict("NOT_CHOOSING", "이미 캐릭터를 골랐어요.");
		}
		jobs.save(GenerationJob.queue(character.getId(), GenerationJob.Type.SPRITE));
	}

	/** 현재 회차의 후보 (선택 중일 때만) */
	@Transactional(readOnly = true)
	public List<CharacterCandidate> currentCandidates(GuestCharacter character) {
		if (character.getStatus() != CharacterStatus.CHOOSING) {
			return List.of();
		}
		return candidates.findByCharacterIdAndRoundOrderBySlot(character.getId(), (short) character.currentRound());
	}

	/** 대기 순번. 대기 중이면 1부터, 생성 중이면 0, 그 외는 null */
	@Transactional(readOnly = true)
	public Long queuePosition(GuestCharacter character) {
		return jobs.findFirstByCharacterIdOrderByIdDesc(character.getId())
				.map(job -> switch (job.getStatus()) {
					case QUEUED -> jobs.countQueuedBefore(job.getId()) + 1;
					case RUNNING -> 0L;
					default -> null;
				})
				.orElse(null);
	}

	public int regenerationsLeft(GuestCharacter character) {
		return Math.max(0, props.guestbook().maxRegenerations() - character.getRegenCount());
	}

	private static String text(String value, String label, int maxLength) {
		String trimmed = value == null ? "" : value.strip();
		if (trimmed.isEmpty() || trimmed.length() > maxLength) {
			throw ApiException.badRequest("INVALID_TEXT", "%s은(는) 1~%d자로 적어 주세요.".formatted(label, maxLength));
		}
		return trimmed;
	}

	private static String photoExtension(MultipartFile photo) {
		String extension = photo == null || photo.isEmpty() ? null : PHOTO_EXTENSIONS.get(photo.getContentType());
		if (extension == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHOTO", "JPG, PNG, WEBP 사진만 올릴 수 있어요.");
		}
		return extension;
	}

	private static byte[] bytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
