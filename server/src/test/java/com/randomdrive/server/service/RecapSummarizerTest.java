package com.randomdrive.server.service;

import com.randomdrive.server.dto.DestinationDto;
import com.randomdrive.server.dto.EventDto;
import com.randomdrive.server.dto.EventType;
import com.randomdrive.server.dto.FinishTripRequest;
import com.randomdrive.server.dto.PointDto;
import com.randomdrive.server.dto.RecapSummaryDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecapSummarizerTest {

    private static final DestinationDto FAR_DEST = new DestinationDto("p", "장소", 38.0, 128.0, "주소", null, 100.0);

    /** 주행 2분 → 같은 자리 6분 → 주행 2분. 점은 1초(주행)/5초(정차) 간격. */
    private static List<PointDto> driveStopDrive(double stopLat) {
        List<PointDto> pts = new ArrayList<>();
        double startLat = stopLat - 119 * 15.0 / 111_000.0;
        for (int i = 0; i < 120; i++) {
            pts.add(new PointDto(startLat + i * 15.0 / 111_000.0, 127.0, i * 1_000L));
        }
        for (int i = 0; i < 72; i++) {
            pts.add(new PointDto(stopLat, 127.0, 121_000L + i * 5_000L));
        }
        for (int i = 0; i < 120; i++) {
            pts.add(new PointDto(stopLat + i * 15.0 / 111_000.0, 127.0, 482_000L + i * 1_000L));
        }
        return pts;
    }

    @Test
    void 거리는_경로_길이_시간은_분_단위_버림_정차는_예상_밖만() {
        List<PointDto> pts = driveStopDrive(37.0);
        FinishTripRequest data = new FinishTripRequest(0L, 601_999L, pts, List.of());

        RecapSummaryDto summary = RecapSummarizer.summarize(FAR_DEST, data);

        // 주행 119초 × 15m × 2구간 = 3,570m
        assertThat(summary.distanceKm()).isEqualTo(3.6);
        assertThat(summary.durationMin()).isEqualTo(10);
        assertThat(summary.stopCount()).isEqualTo(1);
    }

    @Test
    void 경유지_이벤트_근처_정차는_세지_않는다() {
        List<PointDto> pts = driveStopDrive(37.0);
        FinishTripRequest data = new FinishTripRequest(0L, 600_000L, pts,
                List.of(new EventDto(EventType.WAYPOINT_ARRIVED, 121_000L, 37.0005, 127.0, "카페")));

        assertThat(RecapSummarizer.summarize(FAR_DEST, data).stopCount()).isZero();
    }

    @Test
    void 목적지_근처_정차는_세지_않는다() {
        DestinationDto dest = new DestinationDto("p", "장소", 37.0, 127.001, "주소", null, 10.0);
        FinishTripRequest data = new FinishTripRequest(0L, 600_000L, driveStopDrive(37.0), List.of());

        assertThat(RecapSummarizer.summarize(dest, data).stopCount()).isZero();
    }

    @Test
    void 점_순서가_섞여_와도_시간순으로_계산한다() {
        List<PointDto> pts = new ArrayList<>(driveStopDrive(37.0));
        java.util.Collections.reverse(pts);
        FinishTripRequest data = new FinishTripRequest(0L, 600_000L, pts, List.of());

        RecapSummaryDto summary = RecapSummarizer.summarize(FAR_DEST, data);
        assertThat(summary.distanceKm()).isEqualTo(3.6);
        assertThat(summary.stopCount()).isEqualTo(1);
    }

    @Test
    void 종료가_시작보다_빠른_잘못된_시간이면_0분() {
        FinishTripRequest data = new FinishTripRequest(10_000L, 0L,
                List.of(new PointDto(37.0, 127.0, 0L), new PointDto(37.0, 127.0, 1L)), List.of());

        assertThat(RecapSummarizer.summarize(FAR_DEST, data).durationMin()).isZero();
    }
}
