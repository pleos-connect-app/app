package com.randomdrive.server.recap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 목적지 대표 사진(TourAPI, https)을 받아 온다. 실패하면 null — 영상은 사진 없는 배경으로 그린다. */
@Component
public class PhotoLoader {

    private static final Logger log = LoggerFactory.getLogger(PhotoLoader.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();

    public BufferedImage load(String url) {
        if (url == null || !url.startsWith("https://")) {
            return null;
        }
        try {
            HttpResponse<byte[]> res = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() / 100 != 2 || res.body().length > MAX_BYTES) {
                log.warn("photo {} rejected: HTTP {} ({} bytes)", url, res.statusCode(), res.body().length);
                return null;
            }
            return ImageIO.read(new ByteArrayInputStream(res.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("photo {} failed: {}", url, e.toString());
            return null;
        }
    }
}
