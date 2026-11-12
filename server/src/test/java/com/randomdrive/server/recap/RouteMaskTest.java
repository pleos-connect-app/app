package com.randomdrive.server.recap;

import com.randomdrive.server.dto.PointDto;
import com.randomdrive.server.service.Geo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 공유 영상에서 출발지 주변 1.5km를 가리는지. */
class RouteMaskTest {

    /** 출발지에서 동쪽으로 0.5km 간격 점 n개(위도 37도에서 경도 1도 ≈ 88.8km). */
    private static List<PointDto> eastward(int n) {
        double stepLng = 0.5 / 88.8;
        return IntStream.range(0, n).mapToObj(i -> new PointDto(37.0, 127.0 + i * stepLng, (long) i)).toList();
    }

    @Test
    void 출발지_1_5km_안의_점은_빠진다() {
        List<PointDto> points = eastward(10); // 0, 0.5, ..., 4.5km
        List<List<PointDto>> segments = RouteMask.visibleSegments(points);

        assertThat(segments).hasSize(1);
        PointDto first = segments.get(0).get(0);
        assertThat(Geo.distanceKm(37.0, 127.0, first.lat(), first.lng())).isGreaterThanOrEqualTo(RouteMask.HIDE_RADIUS_KM);
        assertThat(segments.get(0)).hasSize(7); // 1.5km ~ 4.5km
    }

    @Test
    void 출발지로_다시_돌아온_구간도_가리고_경로는_조각난다() {
        List<PointDto> out = eastward(8);
        List<PointDto> back = out.reversed();
        List<PointDto> again = eastward(8);
        List<PointDto> trip = new java.util.ArrayList<>();
        trip.addAll(out);
        trip.addAll(back);
        trip.addAll(again);

        List<List<PointDto>> segments = RouteMask.visibleSegments(trip);

        assertThat(segments).hasSize(2);
        segments.forEach(seg -> seg.forEach(p ->
                assertThat(RouteMask.isHidden(trip.get(0), p.lat(), p.lng())).isFalse()));
    }

    @Test
    void 출발지_근처만_돌았으면_보일_경로가_없다() {
        assertThat(RouteMask.visibleSegments(eastward(3))).isEmpty();
        assertThat(RouteMask.visibleSegments(List.of())).isEmpty();
    }
}
