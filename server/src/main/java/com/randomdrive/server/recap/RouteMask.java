package com.randomdrive.server.recap;

import com.randomdrive.server.dto.PointDto;
import com.randomdrive.server.service.Geo;

import java.util.ArrayList;
import java.util.List;

/**
 * 공유되는 영상에서 출발지(대개 집·회사)가 드러나지 않게 경로를 가린다.
 * 출발 좌표(points[0])에서 직선 HIDE_RADIUS_KM 안에 있는 점은 그리지 않는다. 출발 쪽만이 아니라
 * 다시 돌아온 구간도 같이 가린다. 경로선은 가려진 곳에서 끊기므로 결과는 여러 조각(polyline)일 수 있다.
 */
public final class RouteMask {

    public static final double HIDE_RADIUS_KM = 1.5;

    private RouteMask() {
    }

    /** points는 시간순. 반환: 보여도 되는 조각들(각 조각은 점 2개 이상). */
    public static List<List<PointDto>> visibleSegments(List<PointDto> points) {
        List<List<PointDto>> segments = new ArrayList<>();
        if (points.isEmpty()) {
            return segments;
        }
        PointDto origin = points.get(0);
        List<PointDto> current = new ArrayList<>();
        for (PointDto p : points) {
            if (isHidden(origin, p.lat(), p.lng())) {
                flush(segments, current);
                current = new ArrayList<>();
            } else {
                current.add(p);
            }
        }
        flush(segments, current);
        return segments;
    }

    public static boolean isHidden(PointDto origin, double lat, double lng) {
        return Geo.distanceKm(origin.lat(), origin.lng(), lat, lng) < HIDE_RADIUS_KM;
    }

    private static void flush(List<List<PointDto>> segments, List<PointDto> current) {
        if (current.size() >= 2) {
            segments.add(current);
        }
    }
}
