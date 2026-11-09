package com.wedding.guestbook.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 개발용: ./storage 폴더에 저장하고 /files/** 로 내려준다 */
@Configuration
public class LocalStorageService implements StorageService, WebMvcConfigurer {

	private static final Path ROOT = Path.of("storage").toAbsolutePath().normalize();

	@Override
	public void put(String key, byte[] content, String contentType) {
		Path path = resolve(key);
		try {
			Files.createDirectories(path.getParent());
			Files.write(path, content);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public byte[] get(String key) {
		try {
			return Files.readAllBytes(resolve(key));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** 상대 경로로 내려서 프론트 개발 서버 프록시(/files → 백엔드)를 그대로 탄다 */
	@Override
	public String publicUrl(String key) {
		return "/files/" + key;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/files/**").addResourceLocations(ROOT.toUri().toString());
	}

	private Path resolve(String key) {
		Path path = ROOT.resolve(key).normalize();
		if (!path.startsWith(ROOT)) {
			throw new IllegalArgumentException("잘못된 저장소 경로: " + key);
		}
		return path;
	}
}
