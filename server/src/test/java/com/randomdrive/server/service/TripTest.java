package com.randomdrive.server.service;

import com.randomdrive.server.dto.DestinationDto;
import com.randomdrive.server.dto.FinishTripRequest;
import com.randomdrive.server.dto.PointDto;
import com.randomdrive.server.dto.TripStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.randomdrive.server.dto.TripStatus.DONE;
import static com.randomdrive.server.dto.TripStatus.FAILED;
import static com.randomdrive.server.dto.TripStatus.PENDING;
import static com.randomdrive.server.dto.TripStatus.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 리캡 상태 흐름. FAILED는 지금 mock에서 HTTP로 만들 방법이 없어 Trip을 직접 검사한다.
 */
class TripTest {

    private static final DestinationDto DEST = new DestinationDto("p", "장소", 37.0, 127.0, "주소", null, 10.0);

    private static FinishTripRequest data(long endedAt) {
        return new FinishTripRequest(0L, endedAt,
                List.of(new PointDto(37.0, 127.0, 0L), new PointDto(37.1, 127.1, endedAt)), List.of());
    }

    private static List<TripStatus> poll(Trip trip, int times) {
        return java.util.stream.IntStream.range(0, times).mapToObj(i -> trip.pollRecap()).toList();
    }

    @Test
    void finish_전에는_조회해도_PENDING이고_진행하지_않는다() {
        Trip trip = new Trip("t_1", DEST);

        assertThat(poll(trip, 5)).containsOnly(PENDING);
        assertThat(trip.finish(data(1))).isEqualTo(PENDING);
        assertThat(poll(trip, 3)).containsExactly(RUNNING, RUNNING, DONE);
    }

    @Test
    void 진행_중이나_완료_후_finish는_멱등이고_데이터도_바꾸지_않는다() {
        Trip trip = new Trip("t_1", DEST);
        FinishTripRequest first = data(1);
        trip.finish(first);
        trip.pollRecap();

        assertThat(trip.finish(data(2))).isEqualTo(RUNNING);
        poll(trip, 2);
        assertThat(trip.finish(data(3))).isEqualTo(DONE);
        assertThat(poll(trip, 2)).containsOnly(DONE);
        assertThat(trip.getFinishData()).isSameAs(first);
    }

    @Test
    void FAILED_후_finish가_오면_새_데이터로_처음부터_다시_생성() {
        Trip trip = new Trip("t_1", DEST);
        trip.finish(data(1));
        trip.pollRecap();
        trip.fail();

        assertThat(poll(trip, 3)).as("FAILED는 조회해도 그대로").containsOnly(FAILED);

        FinishTripRequest retry = data(2);
        assertThat(trip.finish(retry)).isEqualTo(PENDING);
        assertThat(trip.getFinishData()).isSameAs(retry);
        assertThat(poll(trip, 3)).containsExactly(RUNNING, RUNNING, DONE);
    }

    @Test
    void finish_전이나_DONE_후의_fail은_무시() {
        Trip trip = new Trip("t_1", DEST);
        trip.fail();
        assertThat(trip.getStatus()).isEqualTo(PENDING);

        trip.finish(data(1));
        poll(trip, 3);
        trip.fail();
        assertThat(trip.getStatus()).isEqualTo(DONE);
    }
}
