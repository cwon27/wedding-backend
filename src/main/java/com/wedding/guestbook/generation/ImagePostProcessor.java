package com.wedding.guestbook.generation;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

/**
 * 생성기 결과를 항상 같은 형식으로 맞춘다. 어떤 생성기를 쓰든 이 단계를 거친다.
 * TODO: 배경 제거, 색 수 줄이기, 스프라이트 프레임 정렬 (실제 AI 결과를 보고 추가)
 */
@Component
public class ImagePostProcessor {

	public static final int CHARACTER_SIZE = 64;
	public static final int SPRITE_COLUMNS = 4;
	public static final int SPRITE_ROWS = 3;

	/** 후보 이미지 → 64x64 PNG (도트가 흐려지지 않게 최근접 이웃으로 축소) */
	public byte[] candidate(byte[] image) {
		return png(fit(read(image), CHARACTER_SIZE, CHARACTER_SIZE));
	}

	/** 스프라이트시트 → 프레임 64x64 기준 4x3 PNG */
	public byte[] spriteSheet(byte[] image) {
		return png(fit(read(image), CHARACTER_SIZE * SPRITE_COLUMNS, CHARACTER_SIZE * SPRITE_ROWS));
	}

	private static BufferedImage fit(BufferedImage src, int width, int height) {
		if (src.getWidth() == width && src.getHeight() == height) {
			return src;
		}
		BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = dst.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(src, 0, 0, width, height, null);
		g.dispose();
		return dst;
	}

	private static BufferedImage read(byte[] bytes) {
		try {
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
			if (img == null) {
				throw new IllegalArgumentException("이미지를 읽을 수 없습니다");
			}
			return img;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static byte[] png(BufferedImage img) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(img, "png", out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
