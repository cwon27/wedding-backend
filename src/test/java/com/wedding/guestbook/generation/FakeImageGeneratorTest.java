package com.wedding.guestbook.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class FakeImageGeneratorTest {

	private final FakeImageGenerator generator = new FakeImageGenerator();

	@Test
	void 후보는_4장_64px() throws IOException {
		var candidates = generator.generateCandidates(new byte[0]);

		assertThat(candidates).hasSize(CharacterImageGenerator.CANDIDATE_COUNT);
		BufferedImage first = ImageIO.read(new ByteArrayInputStream(candidates.getFirst()));
		assertThat(first.getWidth()).isEqualTo(64);
		assertThat(first.getHeight()).isEqualTo(64);
	}

	@Test
	void 스프라이트시트는_4열_3행() throws IOException {
		BufferedImage sheet = ImageIO.read(new ByteArrayInputStream(generator.generateSpriteSheet(new byte[0])));

		assertThat(sheet.getWidth()).isEqualTo(64 * 4);
		assertThat(sheet.getHeight()).isEqualTo(64 * 3);
	}
}
