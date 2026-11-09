package com.wedding.guestbook.storage;

/**
 * 하객 사진, 생성된 캐릭터 이미지 저장소.
 * 개발: 로컬 폴더 (LocalStorageService) / 배포: S3 (나중에 S3StorageService 추가)
 */
public interface StorageService {

	/** key 경로에 저장한다. 예: characters/{id}/sprite.webp */
	void put(String key, byte[] content, String contentType);

	byte[] get(String key);

	/** 브라우저가 이미지를 받아갈 수 있는 주소 */
	String publicUrl(String key);
}
