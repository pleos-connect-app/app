package com.randomdrive.server.recap;

import com.randomdrive.server.dto.DestinationDto;
import com.randomdrive.server.dto.EventDto;
import com.randomdrive.server.dto.EventType;
import com.randomdrive.server.dto.FinishTripRequest;
import com.randomdrive.server.dto.PointDto;
import com.randomdrive.server.service.Geo;
import com.randomdrive.server.dto.RecapSummaryDto;
import com.randomdrive.server.dto.SharePageView;
import com.randomdrive.server.service.ShareProperties;
import com.randomdrive.server.service.StopDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 리캡 영상(F-07)·썸네일(handoff 3-2) 파일을 만든다. 파일 이름은 공유 토큰: {storageDir}/{token}.mp4, {token}.jpg.
 * - ffmpeg가 없거나 recap.video=false면 영상 없이 썸네일만 만든다(그래도 리캡은 DONE).
 * - 출발지 1.5km 안의 경로·정차는 그리지 않는다(RouteMask). 출발지는 동네 이름으로만 쓴다.
 */
@Service
public class RecapMediaService {

    private static final Logger log = LoggerFactory.getLogger(RecapMediaService.class);
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{22}");

    /** 만들어진 파일. */
    public record Media(boolean video, boolean thumbnail) {
    }

    private final RecapProperties props;
    private final VWorldClient vworld;
    private final PhotoLoader photos;
    private final RecapPainter painter;
    private final FfmpegEncoder encoder;
    private final boolean videoAvailable;
    private final Path dir;
    private final ShareProperties share;
    private final Clock clock;

    public RecapMediaService(RecapProperties props, ShareProperties share, VWorldClient vworld, PhotoLoader photos,
                             Clock clock) throws IOException {
        this.props = props;
        this.share = share;
        this.clock = clock;
        this.vworld = vworld;
        this.photos = photos;
        this.painter = new RecapPainter(props.fontFile());
        this.encoder = new FfmpegEncoder(props.ffmpegPath());
        this.dir = props.storageDir().toAbsolutePath().normalize();
        Files.createDirectories(dir);
        this.videoAvailable = props.video() && encoder.available();
        if (props.video() && !videoAvailable) {
            log.warn("ffmpeg not found at '{}' — recaps will have a thumbnail but no video. "
                    + "Install ffmpeg or set recap.ffmpeg-path / FFMPEG_PATH.", props.ffmpegPath());
        }
        log.info("Recap media: dir={}, video={}, thumbnail={}, map tiles={}",
                dir, videoAvailable, props.thumbnail(), vworld.enabled());
    }

    /**
     * 공유 기간이 지난 영상·썸네일 파일을 지운다(공유 페이지는 DB 기록으로 410 안내를 계속 보여 준다).
     * 서버가 켜질 때 한 번, 이후 6시간마다.
     */
    @Scheduled(initialDelay = 0, fixedDelay = 6, timeUnit = TimeUnit.HOURS)
    public void deleteExpiredMedia() {
        deleteOlderThan(clock.instant().minus(share.ttl()).toEpochMilli());
    }

    public boolean videoAvailable() {
        return videoAvailable;
    }

    /**
     * 영상·썸네일을 만든다. 실패하면 만들던 파일을 지우고 예외를 던진다(호출 측이 리캡을 FAILED로).
     *
     * @param stops 요약에 센 예상 밖 정차(같은 규칙으로 계산된 위치)
     */
    public Media render(String token, DestinationDto destination, String imageLicense, FinishTripRequest data,
                        RecapSummaryDto summary, List<StopDetector.Stop> stops) throws IOException {
        requireToken(token);
        if (!props.thumbnail() && !videoAvailable) {
            return new Media(false, false);
        }
        long started = System.currentTimeMillis();
        try {
            RecapScene scene = buildScene(destination, imageLicense, data, summary, stops);
            List<double[]> fitPoints = fitPoints(scene);
            boolean thumbnail = false;
            if (props.thumbnail()) {
                MapView square = MapView.fit(fitPoints, RecapPainter.THUMB_H, RecapPainter.THUMB_H, 70);
                BufferedImage squareBackdrop = scene.photo() == null ? square.renderBackdrop(tiles(null)) : null;
                writeJpeg(painter.renderThumbnail(scene, square, squareBackdrop), thumbnailFile(token));
                thumbnail = true;
            }
            boolean video = false;
            if (videoAvailable) {
                RecapPainter.RouteStage stage = routeStage(scene);
                Path tmp = dir.resolve(token + ".tmp.mp4");
                encoder.encode(RecapPainter.VIDEO_W, RecapPainter.VIDEO_H, RecapPainter.FPS, tmp,
                        sink -> painter.renderVideo(scene, stage, sink));
                Files.move(tmp, videoFile(token), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                video = true;
            }
            log.info("Recap media {}: video={}, thumbnail={}, {}ms", token.substring(0, 4) + "…", video, thumbnail,
                    System.currentTimeMillis() - started);
            return new Media(video, thumbnail);
        } catch (IOException | RuntimeException e) {
            delete(token);
            throw e;
        }
    }

    public Path videoFile(String token) {
        requireToken(token);
        return dir.resolve(token + ".mp4");
    }

    public Path thumbnailFile(String token) {
        requireToken(token);
        return dir.resolve(token + ".jpg");
    }

    public void delete(String token) {
        try {
            Files.deleteIfExists(videoFile(token));
            Files.deleteIfExists(thumbnailFile(token));
            Files.deleteIfExists(dir.resolve(token + ".tmp.mp4"));
        } catch (IOException e) {
            log.warn("could not delete recap media: {}", e.toString());
        }
    }

    RecapScene buildScene(DestinationDto destination, String imageLicense, FinishTripRequest data,
                          RecapSummaryDto summary, List<StopDetector.Stop> stops) {
        List<PointDto> points = data.points().stream().sorted(Comparator.comparingLong(PointDto::ts)).toList();
        PointDto origin = points.get(0);

        List<List<double[]>> route = RouteMask.visibleSegments(points).stream()
                .map(seg -> seg.stream().map(p -> new double[]{p.lat(), p.lng()}).toList())
                .toList();
        List<double[]> visibleStops = stops.stream()
                .filter(s -> !RouteMask.isHidden(origin, s.lat(), s.lng()))
                .map(s -> new double[]{s.lat(), s.lng()})
                .toList();
        boolean arrived = data.events().stream().anyMatch(e -> e.type() == EventType.DESTINATION_ARRIVED);
        BufferedImage photo = props.downloadPhotos() ? photos.load(destination.imageUrl()) : null;

        return new RecapScene(
                destination.name(),
                vworld.areaName(origin.lat(), origin.lng()),
                SharePageView.formatDate(data.endedAt()),
                SharePageView.formatKm(summary.distanceKm()),
                SharePageView.formatMinutes(summary.durationMin()),
                summary.stopCount(),
                arrived,
                photo,
                "Type3".equals(imageLicense),
                route,
                visibleStops,
                new double[]{destination.lat(), destination.lng()},
                waypoints(data.events(), origin).stream()
                        .map(w -> new RecapScene.Waypoint(displayName(w.name(), () -> vworld.areaName(w.lat(), w.lng())),
                                w.lat(), w.lng()))
                        .toList());
    }

    /**
     * 경유지 표시 이름. 내비가 주소를 이름으로 주면("서울특별시 양천구 목동중앙남로 11-15 (목동)") 이름표가 잘려
     * 알아보기 어려우므로 동네 이름("양천구 목동")으로 바꾼다. 장소 이름("스타벅스 강남점")은 그대로.
     */
    static String displayName(String name, java.util.function.Supplier<String> areaName) {
        if (!looksLikeAddress(name)) {
            return name;
        }
        String area = areaName.get();
        if (area != null && !area.isBlank()) {
            return area;
        }
        // 동네 이름을 못 구하면 시·도를 떼고, 괄호 안 동 이름이 있으면 그걸 붙인다
        String rest = name.replaceFirst("^\\S+(특별시|광역시|특별자치시|특별자치도|도)\\s+", "");
        return rest.replaceAll("\\s*\\(.*\\)\\s*$", "").trim();
    }

    /** 시·도로 시작하는 문자열 = 주소. */
    static boolean looksLikeAddress(String name) {
        return name.matches("^\\S+(특별시|광역시|특별자치시|특별자치도|도)\\s+.*");
    }

    /**
     * 경유지 이벤트(WAYPOINT_ADDED·ARRIVED)에서 영상에 그릴 경유지를 고른다.
     * 좌표를 모르는 것(앱이 0,0으로 보냄 — 에뮬레이터 내비는 경유지 좌표를 주지 않음)과 한국 밖 좌표는 빼고,
     * 같은 이름이나 100m 안의 같은 곳은 한 번만, 출발지 가림 영역(1.5km) 안은 그리지 않는다.
     */
    static List<RecapScene.Waypoint> waypoints(List<EventDto> events, PointDto origin) {
        List<RecapScene.Waypoint> out = new ArrayList<>();
        for (EventDto e : events) {
            if (e.type() != EventType.WAYPOINT_ADDED && e.type() != EventType.WAYPOINT_ARRIVED) {
                continue;
            }
            if (!inKorea(e.lat(), e.lng()) || RouteMask.isHidden(origin, e.lat(), e.lng())) {
                continue;
            }
            if (out.size() >= MAX_WAYPOINTS) {
                break;
            }
            String name = e.name() == null || e.name().isBlank() ? "경유지" : e.name().trim();
            if (name.length() > MAX_WAYPOINT_NAME) {
                name = name.substring(0, MAX_WAYPOINT_NAME); // 이름은 앱이 보내는 값 — 그리는 비용이 커지지 않게 자른다
            }
            String n = name;
            boolean dup = out.stream().anyMatch(w -> w.name().equals(n)
                    || Geo.distanceKm(w.lat(), w.lng(), e.lat(), e.lng()) < 0.1);
            if (!dup) {
                out.add(new RecapScene.Waypoint(n, e.lat(), e.lng()));
            }
        }
        return out;
    }

    /** 영상에 그리는 경유지 수·이름 길이 상한(입력은 앱이 보내는 값이라 렌더링 비용을 묶어 둔다). */
    static final int MAX_WAYPOINTS = 10;
    static final int MAX_WAYPOINT_NAME = 40;

    private static boolean inKorea(double lat, double lng) {
        return lat >= 33 && lat <= 39 && lng >= 124 && lng <= 132;
    }

    /** 지도에 꼭 들어가야 하는 점: 보이는 경로, 정차, 목적지. */
    private static List<double[]> fitPoints(RecapScene scene) {
        List<double[]> pts = new ArrayList<>();
        scene.route().forEach(pts::addAll);
        pts.addAll(scene.stops());
        scene.waypoints().forEach(w -> pts.add(w.latLng()));
        pts.add(scene.destination());
        return pts;
    }

    /** 카메라 움직임을 정하고, 그 카메라들이 볼 지도 타일을 미리 받아 둔다(키가 없으면 격자 바닥). */
    private RecapPainter.RouteStage routeStage(RecapScene scene) {
        RouteCamera plan = RouteCamera.plan(scene, RecapPainter.VIDEO_W, RecapPainter.VIDEO_H,
                RecapPainter.ROUTE_FRAMES, RecapPainter.FPS);
        TileCache cache = new TileCache();
        PerspectiveMap map = new PerspectiveMap(RecapPainter.VIDEO_W, RecapPainter.VIDEO_H, cache);
        MapView.TileSource source = tiles(null);
        if (source != null) {
            Set<Long> needed = new LinkedHashSet<>();
            for (int i = 0; i < plan.frames(); i += 2) {
                map.collectTiles(plan.camera(i), needed);
            }
            map.collectTiles(plan.camera(plan.frames() - 1), needed);
            long started = System.currentTimeMillis();
            cache.prefetch(needed, source, props.maxTiles());
            log.info("Recap map tiles: {} needed, {} fetched, {}ms", needed.size(), cache.size(),
                    System.currentTimeMillis() - started);
        }
        return new RecapPainter.RouteStage(plan, map, cache.size() > 0);
    }

    private MapView.TileSource tiles(AtomicInteger drawn) {
        if (!vworld.enabled()) {
            return null;
        }
        return (z, x, y) -> {
            BufferedImage t = vworld.satelliteTile(z, x, y);
            if (t != null && drawn != null) {
                drawn.incrementAndGet();
            }
            return t;
        };
    }

    private static void writeJpeg(BufferedImage img, Path target) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(tmp.toFile())) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.88f);
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** 수정 시각이 cutoff보다 오래된 파일을 지운다(파일은 리캡 완료 때 한 번 쓰이므로 완료 시각과 같다). */
    private void deleteOlderThan(long cutoffMillis) {
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile).forEach(f -> {
                try {
                    FileTime modified = Files.getLastModifiedTime(f);
                    if (modified.toMillis() < cutoffMillis) {
                        Files.delete(f);
                    }
                } catch (IOException e) {
                    log.debug("could not clean {}: {}", f, e.toString());
                }
            });
        } catch (IOException e) {
            log.warn("could not clean recap media dir: {}", e.toString());
        }
    }

    private static void requireToken(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new IllegalArgumentException("invalid share token");
        }
    }
}
