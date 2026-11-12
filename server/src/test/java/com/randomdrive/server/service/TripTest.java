package com.randomdrive.server.service;

import com.randomdrive.server.dto.DestinationDto;
import com.randomdrive.server.dto.FinishTripRequest;
import com.randomdrive.server.dto.PointDto;
import com.randomdrive.server.dto.RecapSummaryDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.randomdrive.server.dto.TripStatus.DONE;
import static com.randomdrive.server.dto.TripStatus.FAILED;
import static com.randomdrive.server.dto.TripStatus.PENDING;
import static com.randomdrive.server.dto.TripStatus.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

/** 리캡 상태 흐름(PENDING → RUNNING → DONE/FAILED)과 세대 번호로 옛 작업 결과를 막는지. */
class TripTest {

    private static final DestinationDto DEST = new DestinationDto("p", "장소", 37.0, 127.0, "주소", null, 10.0);
    private static final Recap RECAP = new Recap("AAAAAAAAAAAAAAAAAAAAAA", new RecapSummaryDto(1.0, 1, 0),
            false, false, Instant.EPOCH);

    private static FinishTripRequest data(long endedAt) {
        return new FinishTripRequest(0L, endedAt,
                List.of(new PointDto(37.0, 127.0, 0L), new PointDto(37.1, 127.1, endedAt)), List.of());
    }

    @Test
    void 처음_finish는_새_세대를_시작하고_작업이_RUNNING을_거쳐_DONE() {
        Trip trip = new Trip("t_1", DEST);
        assertThat(trip.getStatus()).isEqualTo(PENDING);

        Trip.FinishOutcome outcome = trip.finish(data(1));
        assertThat(outcome.status()).isEqualTo(PENDING);
        assertThat(outcome.newGeneration()).isEqualTo(1);

        assertThat(trip.startGenerating(1)).isNotNull();
        assertThat(trip.getStatus()).isEqualTo(RUNNING);
        assertThat(trip.complete(1, RECAP)).isTrue();
        assertThat(trip.getStatus()).isEqualTo(DONE);
        assertThat(trip.getRecap()).isSameAs(RECAP);
    }

    @Test
    void 진행_중이나_완료_후_finish는_멱등이고_데이터도_바꾸지_않는다() {
        Trip trip = new Trip("t_1", DEST);
        FinishTripRequest first = data(1);
        trip.finish(first);
        trip.startGenerating(1);

        Trip.FinishOutcome running = trip.finish(data(2));
        assertThat(running.status()).isEqualTo(RUNNING);
        assertThat(running.newGeneration()).as("새 작업을 시작하지 않음").isNull();

        trip.complete(1, RECAP);
        assertThat(trip.finish(data(3)).status()).isEqualTo(DONE);
        assertThat(trip.getFinishData()).isSameAs(first);
    }

    @Test
    void FAILED_후_finish가_오면_새_데이터로_새_세대를_시작하고_옛_작업_결과는_버린다() {
        Trip trip = new Trip("t_1", DEST);
        trip.finish(data(1));
        trip.startGenerating(1);
        trip.fail(1);
        assertThat(trip.getStatus()).isEqualTo(FAILED);

        FinishTripRequest retry = data(2);
        Trip.FinishOutcome outcome = trip.finish(retry);
        assertThat(outcome.status()).isEqualTo(PENDING);
        assertThat(outcome.newGeneration()).isEqualTo(2);
        assertThat(trip.getFinishData()).isSameAs(retry);

        // 1세대 작업이 늦게 끝나도 2세대 상태를 건드리지 못한다.
        assertThat(trip.complete(1, RECAP)).isFalse();
        trip.fail(1);
        assertThat(trip.getStatus()).isEqualTo(PENDING);

        assertThat(trip.startGenerating(1)).as("옛 세대는 시작 불가").isNull();
        assertThat(trip.startGenerating(2)).isSameAs(retry);
        assertThat(trip.complete(2, RECAP)).isTrue();
        assertThat(trip.getStatus()).isEqualTo(DONE);
    }

    @Test
    void finish_전이나_DONE_후의_fail은_무시() {
        Trip trip = new Trip("t_1", DEST);
        trip.fail(0);
        assertThat(trip.getStatus()).isEqualTo(PENDING);

        trip.finish(data(1));
        trip.startGenerating(1);
        trip.complete(1, RECAP);
        trip.fail(1);
        assertThat(trip.getStatus()).isEqualTo(DONE);
    }
}
