package com.randomdrive.server.recap;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 외부 ffmpeg 프로세스로 프레임(BGR 원본)을 받아 MP4(H.264, yuv420p, faststart)로 만든다.
 * 휴대폰 브라우저에서 바로 재생되도록 yuv420p와 faststart(앞부분에 메타데이터)를 쓴다.
 */
public final class FfmpegEncoder {

    private static final long TIMEOUT_S = 120;

    private final String ffmpegPath;

    public FfmpegEncoder(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    /** ffmpeg를 실행할 수 있는지. 서버 시작 때 한 번 확인한다. */
    public boolean available() {
        try {
            Process p = new ProcessBuilder(ffmpegPath, "-version").redirectErrorStream(true).start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 프레임을 받아 쓰는 작업. 끝나면 out에 MP4가 생긴다. */
    @FunctionalInterface
    public interface FrameProducer {
        void produce(RecapPainter.FrameSink sink) throws IOException;
    }

    public void encode(int width, int height, int fps, Path out, FrameProducer producer) throws IOException {
        Path errLog = Files.createTempFile("ffmpeg-", ".log");
        List<String> cmd = List.of(ffmpegPath, "-y", "-loglevel", "error",
                "-f", "rawvideo", "-pix_fmt", "bgr24", "-s", width + "x" + height, "-r", String.valueOf(fps),
                "-i", "pipe:0",
                "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                "-pix_fmt", "yuv420p", "-movflags", "+faststart",
                out.toString());
        Process p = new ProcessBuilder(cmd)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(errLog.toFile())
                .start();
        try {
            try (OutputStream stdin = p.getOutputStream()) {
                producer.produce(frame -> {
                    if (frame.getType() != BufferedImage.TYPE_3BYTE_BGR
                            || frame.getWidth() != width || frame.getHeight() != height) {
                        throw new IllegalArgumentException("frame must be TYPE_3BYTE_BGR " + width + "x" + height);
                    }
                    stdin.write(((DataBufferByte) frame.getRaster().getDataBuffer()).getData());
                });
            }
            if (!p.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) {
                throw new IOException("ffmpeg timed out");
            }
            if (p.exitValue() != 0) {
                throw new IOException("ffmpeg exited " + p.exitValue() + ": " + tail(errLog));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while encoding", e);
        } catch (IOException e) {
            // stdin 쓰기 중 ffmpeg가 죽으면 "파이프 끊김"만 보이므로 ffmpeg 로그를 붙인다.
            throw new IOException(e.getMessage() + " / ffmpeg: " + tail(errLog), e);
        } finally {
            p.destroyForcibly();
            Files.deleteIfExists(errLog);
        }
    }

    private static String tail(Path log) {
        try {
            String s = Files.readString(log).strip();
            return s.length() > 500 ? s.substring(s.length() - 500) : s;
        } catch (IOException e) {
            return "(no log)";
        }
    }
}
