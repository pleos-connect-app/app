package com.randomdrive.server.service;

import com.randomdrive.server.dto.PointDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 앱 StopDetectorTest.kt와 같은 케이스. 앱과 서버의 정차 수가 같아야 한다. */
class StopDetectorTest {

    /** 북쪽으로 1초에 15m씩 이동하는 주행 구간. */
    private static List<PointDto> drive(double fromLat, long startTs, int seconds) {
        List<PointDto> list = new ArrayList<>();
        for (int i = 0; i < seconds; i++) {
            list.add(new PointDto(fromLat + (i * 15.0) / 111_000.0, 127.0, startTs + i * 1_000L));
        }
        return list;
    }

    private static List<PointDto> stay(double lat, long startTs, int seconds) {
        List<PointDto> list = new ArrayList<>();
        for (int i = 0; i < seconds / 5; i++) {
            list.add(new PointDto(lat, 127.0, startTs + i * 5_000L));
        }
        return list;
    }

    @SafeVarargs
    private static List<PointDto> concat(List<PointDto>... parts) {
        List<PointDto> all = new ArrayList<>();
        for (List<PointDto> p : parts) {
            all.addAll(p);
        }
        return all;
    }

    private static double lastLat(List<PointDto> points) {
        return points.getLast().lat();
    }

    @Test
    void 주행_6분정차_주행이면_정차_1회() {
        List<PointDto> a = drive(37.0, 0, 120);
        double stopLat = lastLat(a);
        List<StopDetector.Stop> stops = StopDetector.detect(
                concat(a, stay(stopLat, 121_000, 360), drive(stopLat, 482_000, 120)), List.of());

        assertThat(stops).hasSize(1);
        assertThat(stops.getFirst().lat()).isCloseTo(stopLat, within(1e-4));
    }

    @Test
    void 신호대기_2분은_정차_아님() {
        List<PointDto> a = drive(37.0, 0, 120);
        assertThat(StopDetector.detect(
                concat(a, stay(lastLat(a), 121_000, 120), drive(lastLat(a), 242_000, 120)), List.of())).isEmpty();
    }

    @Test
    void 출발_전_대기와_도착_후_체류는_제외() {
        List<PointDto> a = drive(37.0, 601_000, 120);
        assertThat(StopDetector.detect(
                concat(stay(37.0, 0, 600), a, stay(lastLat(a), 722_000, 600)), List.of())).isEmpty();
    }

    @Test
    void 계획_경유지_근처_정차는_예상_밖이_아님() {
        List<PointDto> a = drive(37.0, 0, 120);
        double stopLat = lastLat(a);
        assertThat(StopDetector.detect(
                concat(a, stay(stopLat, 121_000, 360), drive(stopLat, 482_000, 120)),
                List.of(new StopDetector.LatLng(stopLat, 127.0)))).isEmpty();
    }

    @Test
    void 점이_3개_미만이면_정차_없음() {
        assertThat(StopDetector.detect(List.of(new PointDto(37.0, 127.0, 0L), new PointDto(37.0, 127.0, 600_000L)),
                List.of())).isEmpty();
    }
}
