package com.wedding.guestbook.generation;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 개발용 가짜 생성기. AI 를 호출하지 않고 단색 사람 모양 도트를 그린다.
 * 비용 없이 업로드 → 대기열 → 화면 표시 흐름 전체를 먼저 완성하기 위해 쓴다.
 */
@Component
@ConditionalOnProperty(name = "app.generation.generator", havingValue = "fake")
public class FakeImageGenerator implements CharacterImageGenerator {

	private static final int SIZE = 64;
	private static final Color[] COLORS = { new Color(0xE57373), new Color(0x64B5F6), new Color(0x81C784),
			new Color(0xFFB74D) };

	@Override
	public List<byte[]> generateCandidates(byte[] photo) {
		return IntStream.range(0, CANDIDATE_COUNT).mapToObj(i -> png(drawPerson(COLORS[i], 0))).toList();
	}

	@Override
	public byte[] generateSpriteSheet(byte[] character) {
		BufferedImage sheet = new BufferedImage(SIZE * 4, SIZE * 3, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = sheet.createGraphics();
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 4; col++) {
				// 걷는 느낌만 나도록 프레임마다 다리 위치를 조금씩 바꾼다
				g.drawImage(drawPerson(COLORS[row], col % 2 == 0 ? 0 : 2), col * SIZE, row * SIZE, null);
			}
		}
		g.dispose();
		return png(sheet);
	}

	private static BufferedImage drawPerson(Color body, int step) {
		BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(0xF2D0B5));
		g.fillRect(24, 8, 16, 16); // 얼굴
		g.setColor(new Color(0x3E2C23));
		g.fillRect(22, 6, 20, 6); //  머리카락
		g.setColor(body);
		g.fillRect(20, 24, 24, 20); // 몸
		g.setColor(new Color(0x2B2B2B));
		g.fillRect(24 - step, 44, 6, 14); // 다리
		g.fillRect(34 + step, 44, 6, 14);
		g.dispose();
		return img;
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
