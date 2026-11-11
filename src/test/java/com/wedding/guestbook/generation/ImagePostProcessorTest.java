package com.wedding.guestbook.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class ImagePostProcessorTest {

	private static final Color MAGENTA = new Color(0xFF00FF);
	private static final Color OUTLINE = new Color(0x222222);

	private final ImagePostProcessor processor = new ImagePostProcessor();

	/** Gemini 결과처럼: 1024px 마젠타 배경 가운데에 외곽선 있는 캐릭터 (안쪽에 마젠타 비슷한 옷) */
	private static BufferedImage aiLikeImage() {
		BufferedImage img = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(MAGENTA);
		g.fillRect(0, 0, 1024, 1024);
		g.setColor(OUTLINE);
		g.fillRect(400, 200, 224, 640); // 외곽선 겸 몸통
		g.setColor(new Color(0xF000F0)); // 배경과 거의 같은 색 옷 — 외곽선에 막혀 지워지면 안 된다
		g.fillRect(420, 400, 184, 200);
		g.dispose();
		return img;
	}

	@Test
	void 배경을_지우고_64px_캔버스_바닥_가운데에_놓는다() throws IOException {
		BufferedImage result = read(processor.candidate(png(aiLikeImage())));

		assertThat(result.getWidth()).isEqualTo(64);
		assertThat(result.getHeight()).isEqualTo(64);
		assertThat(alpha(result, 0, 0)).as("모서리 배경은 투명").isZero();
		assertThat(alpha(result, 32, 63)).as("발(맨 아래 가운데)은 캐릭터").isEqualTo(255);
		assertThat(alpha(result, 32, 0)).as("세로가 꽉 차도록 축소").isEqualTo(255);
	}

	@Test
	void 외곽선_안쪽의_배경과_비슷한_색은_남긴다() throws IOException {
		BufferedImage result = read(processor.candidate(png(aiLikeImage())));

		// 원본 (420~604, 400~600) 영역 → 64px 로 축소된 위치의 가운데
		assertThat(alpha(result, 32, 30)).isEqualTo(255);
	}

	@Test
	void 이미_작은_이미지는_키우지_않는다() throws IOException {
		BufferedImage small = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = small.createGraphics();
		g.setColor(OUTLINE);
		g.fillRect(20, 10, 20, 40); // 20x40 캐릭터, 배경 투명
		g.dispose();

		BufferedImage result = read(processor.candidate(png(small)));

		assertThat(alpha(result, 32, 63)).isEqualTo(255);
		assertThat(alpha(result, 32, 63 - 40)).as("높이 40 그대로, 그 위는 투명").isZero();
	}

	@Test
	void 캐릭터가_없으면_실패한다() {
		BufferedImage empty = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = empty.createGraphics();
		g.setColor(MAGENTA);
		g.fillRect(0, 0, 100, 100);
		g.dispose();

		assertThatThrownBy(() -> processor.candidate(png(empty))).isInstanceOf(IllegalStateException.class);
	}

	private static int alpha(BufferedImage img, int x, int y) {
		return (img.getRGB(x, y) >>> 24) & 0xFF;
	}

	private static byte[] png(BufferedImage img) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "png", out);
		return out.toByteArray();
	}

	private static BufferedImage read(byte[] bytes) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(bytes));
	}
}
