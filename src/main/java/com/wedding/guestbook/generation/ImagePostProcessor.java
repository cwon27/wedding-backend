package com.wedding.guestbook.generation;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

/**
 * 생성기 결과를 항상 같은 형식으로 맞춘다. 어떤 생성기를 쓰든 이 단계를 거친다.
 *
 * <p>후보: 배경 제거 → 캐릭터 영역만 자르기 → 64x64 안에 발이 바닥에 닿게 배치
 * <p>스프라이트시트: 4x3 시트 크기로 맞추기 (TODO: 프레임 정렬은 PixelLab 결과를 보고 추가)
 */
@Component
public class ImagePostProcessor {

	public static final int CHARACTER_SIZE = 64;
	public static final int SPRITE_COLUMNS = 4;
	public static final int SPRITE_ROWS = 3;

	/** 배경색과 이 거리(RGB) 안이면 배경으로 본다. AI 이미지는 배경색이 미세하게 흔들려서 여유를 둔다 */
	private static final int BACKGROUND_TOLERANCE = 90;
	private static final int TRANSPARENT = 0x00000000;

	public byte[] candidate(byte[] image) {
		BufferedImage src = toArgb(read(image));
		removeBackground(src);
		return png(fitBottomCenter(cropToContent(src), CHARACTER_SIZE));
	}

	public byte[] spriteSheet(byte[] image) {
		return png(resizeNearest(toArgb(read(image)), CHARACTER_SIZE * SPRITE_COLUMNS, CHARACTER_SIZE * SPRITE_ROWS));
	}

	/**
	 * 테두리에서 시작해 배경색과 비슷한 픽셀을 따라가며(flood fill) 투명하게 만든다.
	 * 테두리와 이어진 영역만 지우므로, 캐릭터 안쪽에 배경과 비슷한 색이 있어도 외곽선에 막혀 남는다.
	 */
	static void removeBackground(BufferedImage img) {
		int w = img.getWidth();
		int h = img.getHeight();
		int background = img.getRGB(0, 0); // 왼쪽 위 모서리를 배경색으로 본다
		boolean[] visited = new boolean[w * h];
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		for (int x = 0; x < w; x++) {
			queue.add(new int[] { x, 0 });
			queue.add(new int[] { x, h - 1 });
		}
		for (int y = 0; y < h; y++) {
			queue.add(new int[] { 0, y });
			queue.add(new int[] { w - 1, y });
		}

		while (!queue.isEmpty()) {
			int[] p = queue.poll();
			int x = p[0];
			int y = p[1];
			if (x < 0 || y < 0 || x >= w || y >= h || visited[y * w + x]) {
				continue;
			}
			visited[y * w + x] = true;
			if (!isBackground(img.getRGB(x, y), background)) {
				continue;
			}
			img.setRGB(x, y, TRANSPARENT);
			queue.add(new int[] { x + 1, y });
			queue.add(new int[] { x - 1, y });
			queue.add(new int[] { x, y + 1 });
			queue.add(new int[] { x, y - 1 });
		}
	}

	private static boolean isBackground(int argb, int background) {
		if (alpha(argb) < 16) {
			return true; // 이미 투명한 픽셀 (fake 생성기, 투명 배경 PNG)
		}
		if (alpha(background) < 16) {
			// 배경이 투명이면 색은 비교하지 않는다. 투명 픽셀의 RGB 는 보통 검정(0,0,0)이라
			// 색으로 비교하면 어두운 외곽선까지 배경으로 지워진다
			return false;
		}
		int dr = ((argb >> 16) & 0xFF) - ((background >> 16) & 0xFF);
		int dg = ((argb >> 8) & 0xFF) - ((background >> 8) & 0xFF);
		int db = (argb & 0xFF) - (background & 0xFF);
		return dr * dr + dg * dg + db * db <= BACKGROUND_TOLERANCE * BACKGROUND_TOLERANCE;
	}

	/** 투명하지 않은 픽셀을 모두 감싸는 가장 작은 사각형으로 자른다 */
	static BufferedImage cropToContent(BufferedImage img) {
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				if (alpha(img.getRGB(x, y)) >= 16) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		if (maxX < 0) {
			throw new IllegalStateException("배경을 지우고 나니 캐릭터가 남지 않았습니다");
		}
		return img.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	/**
	 * size x size 캔버스 안에 비율을 유지해 넣는다. 가로는 가운데, 세로는 바닥에 붙인다
	 * (하객 화면이 캐릭터의 발 위치를 기준점으로 쓰기 때문). 이미 작으면 키우지 않는다(도트가 뭉개짐).
	 */
	static BufferedImage fitBottomCenter(BufferedImage img, int size) {
		double scale = Math.min(1.0, Math.min((double) size / img.getWidth(), (double) size / img.getHeight()));
		int w = Math.max(1, (int) Math.round(img.getWidth() * scale));
		int h = Math.max(1, (int) Math.round(img.getHeight() * scale));
		BufferedImage scaled = resizeNearest(img, w, h);

		BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		int offsetX = (size - w) / 2;
		int offsetY = size - h;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				canvas.setRGB(offsetX + x, offsetY + y, scaled.getRGB(x, y));
			}
		}
		return canvas;
	}

	/**
	 * 최근접 이웃 축소: 결과 픽셀마다 원본에서 가장 가까운 픽셀 하나를 그대로 가져온다.
	 * 주변 색을 섞지 않아서 도트의 선명한 색이 유지된다 (부드러운 축소는 흐려진다).
	 */
	static BufferedImage resizeNearest(BufferedImage src, int width, int height) {
		if (src.getWidth() == width && src.getHeight() == height) {
			return src;
		}
		BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			int sy = Math.min(src.getHeight() - 1, (int) ((y + 0.5) * src.getHeight() / height));
			for (int x = 0; x < width; x++) {
				int sx = Math.min(src.getWidth() - 1, (int) ((x + 0.5) * src.getWidth() / width));
				dst.setRGB(x, y, src.getRGB(sx, sy));
			}
		}
		return dst;
	}

	private static int alpha(int argb) {
		return (argb >>> 24) & 0xFF;
	}

	/** 어떤 형식이든 ARGB 로 바꿔서 투명도를 다룰 수 있게 한다 */
	private static BufferedImage toArgb(BufferedImage src) {
		if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
			return src;
		}
		BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		dst.getGraphics().drawImage(src, 0, 0, null);
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
