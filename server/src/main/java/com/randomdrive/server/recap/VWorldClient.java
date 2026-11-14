package com.randomdrive.server.recap;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 공간정보 오픈플랫폼(VWorld, 국토교통부) 연동. 앱과 같은 서비스를 쓴다.
 * - 위성 지도 타일(WMTS): 리캡 영상의 경로 배경
 * - 역지오코딩: 출발지 좌표 → "용산구 동자동" (handoff 3-3, 앱은 출발지 이름을 보내지 않는다)
 *
 * 인증키는 환경변수 VWORLD_API_KEY 또는 application-local.yml(vworld.api-key). 없으면 둘 다 null을 돌려주고,
 * 호출 측은 지도 없는 배경·출발지 이름 없는 문구로 대신한다.
 */
@Component
public class VWorldClient {

    public static final String ATTRIBUTION = "지도: 브이월드(국토교통부)";

    private static final Logger log = LoggerFactory.getLogger(VWorldClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_TILE_BYTES = 1024 * 1024;

    private final String apiKey;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    public VWorldClient(@Value("${vworld.api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public boolean enabled() {
        return !apiKey.isEmpty();
    }

    /** 위성 타일 한 장(256px). 키가 없거나 실패하면 null. */
    public BufferedImage satelliteTile(int z, int x, int y) {
        byte[] jpeg = satelliteTileBytes(z, x, y);
        if (jpeg == null) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(jpeg));
        } catch (Exception e) {
            log.debug("VWorld tile {}/{}/{} decode failed: {}", z, x, y, e.toString());
            return null;
        }
    }

    /** 위성 타일 원본 JPEG(앱 지도 중계용 — 키는 서버에만 둔다). 키가 없거나 실패하면 null. */
    public byte[] satelliteTileBytes(int z, int x, int y) {
        if (!enabled()) {
            return null;
        }
        String url = "https://api.vworld.kr/req/wmts/1.0.0/" + apiKey + "/Satellite/" + z + "/" + y + "/" + x + ".jpeg";
        try {
            HttpResponse<byte[]> res = http.send(get(url), HttpResponse.BodyHandlers.ofByteArray());
            String type = res.headers().firstValue("Content-Type").orElse("");
            // 키 오류면 200이어도 XML이 온다.
            if (res.statusCode() / 100 != 2 || !type.startsWith("image/") || res.body().length > MAX_TILE_BYTES) {
                log.debug("VWorld tile {}/{}/{} rejected: HTTP {} {}", z, x, y, res.statusCode(), type);
                return null;
            }
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.debug("VWorld tile {}/{}/{} failed: {}", z, x, y, e.toString());
            return null;
        }
    }

    /** 행정구역 이름. [full]="서울특별시 용산구 동자동", [shortName]="용산구 동자동". */
    public record Area(String full, String shortName) {
    }

    /** 좌표의 "구 동" 이름(예: "용산구 동자동"). 바다·키 없음·실패면 null. */
    public String areaName(double lat, double lng) {
        Area a = area(lat, lng);
        return a == null ? null : a.shortName();
    }

    /** 좌표의 행정구역 이름(전체·짧은 이름). 바다·키 없음·실패면 null. */
    public Area area(double lat, double lng) {
        if (!enabled()) {
            return null;
        }
        String url = String.format(Locale.ROOT,
                "https://api.vworld.kr/req/address?service=address&request=getAddress&version=2.0&crs=epsg:4326"
                        + "&point=%.6f,%.6f&format=json&type=PARCEL&zipcode=false&simple=false&key=%s",
                lng, lat, apiKey);
        try {
            HttpResponse<String> res = http.send(get(url), HttpResponse.BodyHandlers.ofString());
            return res.statusCode() / 100 == 2 ? parseArea(res.body()) : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("VWorld reverse geocode failed: {}", e.toString());
            return null;
        }
    }

    /** 짧은 이름만(영상 출발지 표시용). */
    String parseAreaName(String body) throws Exception {
        Area a = parseArea(body);
        return a == null ? null : a.shortName();
    }

    /**
     * 전체 이름: level1(시·도) + level2(구) + level3 + level4L(동·읍·면).
     * 짧은 이름: level2 + level4L, 둘 다 없으면 시·도. 결과가 없으면 null.
     */
    Area parseArea(String body) throws Exception {
        JsonNode res = json.readTree(body).path("response");
        if (!"OK".equals(res.path("status").asString(""))) {
            return null;
        }
        JsonNode s = res.path("result").path(0).path("structure");
        String full = join(s.path("level1").asString(""), s.path("level2").asString(""),
                s.path("level3").asString(""), s.path("level4L").asString(""));
        String shortName = join(s.path("level2").asString(""), s.path("level4L").asString(""));
        if (shortName.isEmpty()) {
            shortName = emptyToNull(s.path("level1").asString(""));
        }
        return full.isEmpty() || shortName == null ? null : new Area(full, shortName);
    }

    private static String join(String... parts) {
        return Stream.of(parts).filter(p -> !p.isBlank()).collect(Collectors.joining(" "));
    }

    private static String emptyToNull(String s) {
        return s.isBlank() ? null : s;
    }

    private static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
    }
}
