package com.randomdrive.server.service;

import com.randomdrive.server.dto.DestinationDto;
import com.randomdrive.server.dto.EventType;
import com.randomdrive.server.dto.FinishTripRequest;
import com.randomdrive.server.dto.PointDto;
import com.randomdrive.server.dto.RecapSummaryDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * finish로 받은 주행 데이터 → 리캡 요약 수치. 앱 MainViewModel.localSummary와 같은 계산(handoff 2-4).
 * - distanceKm: points를 이은 총 길이 (소수 첫째 자리 반올림)
 * - durationMin: endedAt - startedAt (분, 버림)
 * - stopCount: 예상 밖 정차 횟수. 목적지와 계획 경유지(WAYPOINT_ADDED/ARRIVED 이벤트 좌표) 근처는 제외
 */
public final class RecapSummarizer {

    private RecapSummarizer() {
    }

    public static RecapSummaryDto summarize(DestinationDto destination, FinishTripRequest data) {
        // 앱은 시간순으로 보내지만, 서버는 받은 순서를 믿지 않고 정렬한다.
        List<PointDto> points = data.points().stream().sorted(Comparator.comparingLong(PointDto::ts)).toList();

        double km = 0;
        for (int i = 1; i < points.size(); i++) {
            PointDto a = points.get(i - 1);
            PointDto b = points.get(i);
            km += Geo.distanceKm(a.lat(), a.lng(), b.lat(), b.lng());
        }

        long durationMin = Math.max(0, (data.endedAt() - data.startedAt()) / 60_000L);
        return new RecapSummaryDto(
                Math.round(km * 10) / 10.0,
                (int) durationMin,
                unexpectedStops(destination, data).size());
    }

    /** 예상 밖 정차 목록. 요약의 stopCount와 영상의 정차 핀이 같은 규칙을 쓰도록 공개한다. */
    public static List<StopDetector.Stop> unexpectedStops(DestinationDto destination, FinishTripRequest data) {
        List<PointDto> points = data.points().stream().sorted(Comparator.comparingLong(PointDto::ts)).toList();
        List<StopDetector.LatLng> planned = new ArrayList<>();
        planned.add(new StopDetector.LatLng(destination.lat(), destination.lng()));
        data.events().stream()
                .filter(e -> e.type() == EventType.WAYPOINT_ADDED || e.type() == EventType.WAYPOINT_ARRIVED)
                .forEach(e -> planned.add(new StopDetector.LatLng(e.lat(), e.lng())));
        return StopDetector.detect(points, planned);
    }
}
