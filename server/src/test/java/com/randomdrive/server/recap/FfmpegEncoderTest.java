package com.randomdrive.server.recap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/** ffmpeg가 설치된 PC에서만 돈다(FFMPEG_PATH 또는 PATH의 ffmpeg). 없으면 건너뛴다. */
class FfmpegEncoderTest {

    @TempDir
    Path dir;

    @Test
    void 프레임을_MP4로_만든다() throws Exception {
        String path = System.getenv().getOrDefault("FFMPEG_PATH", "ffmpeg");
        FfmpegEncoder encoder = new FfmpegEncoder(path);
        assumeThat(encoder.available()).as("ffmpeg 없음 — 건너뜀").isTrue();

        Path out = dir.resolve("t.mp4");
        encoder.encode(64, 96, 10, out, sink -> {
            BufferedImage f = new BufferedImage(64, 96, BufferedImage.TYPE_3BYTE_BGR);
            for (int i = 0; i < 10; i++) {
                Graphics2D g = f.createGraphics();
                g.setColor(new Color(i * 20, 100, 200));
                g.fillRect(0, 0, 64, 96);
                g.dispose();
                sink.accept(f);
            }
        });

        byte[] head = Arrays.copyOf(Files.readAllBytes(out), 12);
        assertThat(new String(head, 4, 4)).isEqualTo("ftyp");
    }

    @Test
    void 없는_ffmpeg는_사용_불가() {
        assertThat(new FfmpegEncoder(dir.resolve("no-ffmpeg").toString()).available()).isFalse();
    }
}
