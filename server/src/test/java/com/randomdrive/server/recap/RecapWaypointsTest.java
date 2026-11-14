package com.randomdrive.server.recap;

import com.randomdrive.server.dto.EventDto;
import com.randomdrive.server.dto.EventType;
import com.randomdrive.server.dto.PointDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 영상에 그릴 경유지 고르기. */
class RecapWaypointsTest {

    private static final PointDto ORIGIN = new PointDto(37.4527, 126.9037, 0L);

    private static EventDto ev(EventType type, String name, double lat, double lng) {
        return new EventDto(type, 1L, lat, lng, name);
    }

    @Test
    void 좌표를_모르는_경유지와_목적지_이벤트는_빼고_같은_곳은_한_번만() {
        List<RecapScene.Waypoint> wps = RecapMediaService.waypoints(List.of(
                ev(EventType.DESTINATION_SET, "장안문", 37.2896, 127.0124),
                ev(EventType.WAYPOINT_ADDED, "목동", 0.0, 0.0),                 // 에뮬레이터: 좌표 없음
                ev(EventType.WAYPOINT_ADDED, "목동중앙남로 11-15", 37.5262, 126.8752),
                ev(EventType.WAYPOINT_ARRIVED, "목동중앙남로 11-15", 37.5262, 126.8752), // 같은 이름
                ev(EventType.WAYPOINT_ADDED, "카페", 37.5265, 126.8755),          // 100m 안
                ev(EventType.WAYPOINT_ADDED, "광교호수공원", 37.2830, 127.0650)
        ), ORIGIN);
        assertThat(wps).extracting(RecapScene.Waypoint::name).containsExactly("목동중앙남로 11-15", "광교호수공원");
    }

    @Test
    void 출발지_가림_영역_안의_경유지는_그리지_않는다() {
        List<RecapScene.Waypoint> wps = RecapMediaService.waypoints(List.of(
                ev(EventType.WAYPOINT_ADDED, "집 앞 편의점", 37.4530, 126.9040)), ORIGIN);
        assertThat(wps).isEmpty();
    }
}
