package com.randomdrive.server.service;

import com.randomdrive.server.dto.PointDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 예상 밖 정차 감지 (MVP F-05의 1차 규칙). 앱 StopDetector.kt를 그대로 옮긴 것이라 숫자가 같아야 한다.
 *
 * - 단일 좌표로 판단하지 않고, RADIUS_M 안에 머문 연속 점 묶음이 MIN_DURATION_MS 이상일 때만 정차로 본다.
 * - 정차 전후에 주행 구간이 있어야 한다(출발 대기·도착 후 체류 제외).
 * - excluded(목적지·계획 경유지) EXCLUDE_RADIUS_M 이내 체류는 "예상 밖"이 아니므로 제외한다.
 *
 * 신호 대기(보통 1~3분 미만)와 구분하려고 최소 체류를 5분으로 둔다. 기준값은 실제 주행 로그로 보정해야 한다.
 */
public final class StopDetector {

    static final double RADIUS_M = 60.0;
    static final long MIN_DURATION_MS = 5 * 60_000L;
    static final double EXCLUDE_RADIUS_M = 300.0;

    public record LatLng(double lat, double lng) {
    }

    public record Stop(double lat, double lng, long startTs, long endTs) {
    }

    private StopDetector() {
    }

    /** points는 시간순이어야 한다. */
    public static List<Stop> detect(List<PointDto> points, List<LatLng> excluded) {
        List<Stop> stops = new ArrayList<>();
        if (points.size() < 3) {
            return stops;
        }
        int i = 0;
        while (i < points.size()) {
            PointDto anchor = points.get(i);
            int j = i + 1;
            while (j < points.size() && meters(anchor.lat(), anchor.lng(), points.get(j).lat(), points.get(j).lng()) <= RADIUS_M) {
                j++;
            }
            PointDto last = points.get(j - 1);
            long duration = last.ts() - anchor.ts();
            boolean hasDriveBefore = i > 0;
            boolean hasDriveAfter = j < points.size();
            if (duration >= MIN_DURATION_MS) {
                // 긴 체류 묶음은 정차로 세든 안 세든 통째로 건너뛴다(묶음 중간점부터 다시 세면 출발 대기 후반부가 정차로 잡힘).
                if (hasDriveBefore && hasDriveAfter) {
                    List<PointDto> cluster = points.subList(i, j);
                    double centerLat = cluster.stream().mapToDouble(PointDto::lat).average().orElseThrow();
                    double centerLng = cluster.stream().mapToDouble(PointDto::lng).average().orElseThrow();
                    boolean nearExcluded = excluded.stream()
                            .anyMatch(e -> meters(e.lat(), e.lng(), centerLat, centerLng) <= EXCLUDE_RADIUS_M);
                    if (!nearExcluded) {
                        stops.add(new Stop(centerLat, centerLng, anchor.ts(), last.ts()));
                    }
                }
                i = j;
            } else {
                i++;
            }
        }
        return stops;
    }

    private static double meters(double lat1, double lng1, double lat2, double lng2) {
        return Geo.distanceKm(lat1, lng1, lat2, lng2) * 1000.0;
    }
}
