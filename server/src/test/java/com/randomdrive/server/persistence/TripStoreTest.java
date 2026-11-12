package com.randomdrive.server.persistence;

import com.randomdrive.server.dto.DestinationDto;
import com.randomdrive.server.dto.EventDto;
import com.randomdrive.server.dto.EventType;
import com.randomdrive.server.dto.FinishTripRequest;
import com.randomdrive.server.dto.PointDto;
import com.randomdrive.server.dto.RecapSummaryDto;
import com.randomdrive.server.dto.TripStatus;
import com.randomdrive.server.service.Recap;
import com.randomdrive.server.service.Trip;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 저장·복원. 서버 재시작은 "캐시가 빈 새 TripStore"로 흉내 낸다(같은 DB를 본다).
 */
@SpringBootTest
class TripStoreTest {

    private static final DestinationDto DEST = new DestinationDto("p1", "남한산성", 37.4786, 127.1817,
            "경기도 광주시", "https://img/x.jpg", 20.4);

    @Autowired
    TripJpaRepository trips;
    @Autowired
    TripFinishJpaRepository finishes;
    @Autowired
    TripStore store;

    private TripStore restarted() {
        return new TripStore(trips, finishes, Clock.systemUTC());
    }

    private static FinishTripRequest data() {
        return new FinishTripRequest(1_000L, 9_000L,
                List.of(new PointDto(37.55, 126.97, 1_000L), new PointDto(37.48, 127.18, 9_000L)),
                List.of(new EventDto(EventType.DESTINATION_ARRIVED, 9_000L, 37.48, 127.18, "남한산성")));
    }

    @Test
    void 완료된_트립은_재시작_후에도_공유_토큰으로_찾고_요약이_같다() {
        Trip trip = new Trip("t_db0001", DEST);
        assertThat(store.insert(trip)).isTrue();
        assertThat(store.insert(new Trip("t_db0001", DEST))).as("같은 id는 거절").isFalse();

        trip.finish(data());
        store.saveFinish(trip, data());
        trip.startGenerating(1);
        Recap recap = new Recap("TOKEN_db0001_xxxxxxxxx", new RecapSummaryDto(25.7, 71, 1), true, true,
                Instant.parse("2026-11-13T00:00:00Z"));
        trip.complete(1, recap);
        store.save(trip);

        Trip loaded = restarted().findByShareToken("TOKEN_db0001_xxxxxxxxx").orElseThrow();
        assertThat(loaded).isNotSameAs(trip);
        assertThat(loaded.getStatus()).isEqualTo(TripStatus.DONE);
        assertThat(loaded.getRecap()).isEqualTo(recap);
        assertThat(loaded.getDestination()).isEqualTo(DEST);
        assertThat(loaded.getEndedAt()).isEqualTo(9_000L);
        assertThat(loaded.getFinishData()).as("DONE은 주행 데이터를 읽지 않음").isNull();
    }

    @Test
    void 생성_중에_재시작되면_대기로_돌아가고_주행_데이터와_함께_다시_생성_대상이_된다() {
        Trip trip = new Trip("t_db0002", DEST);
        store.insert(trip);
        trip.finish(data());
        store.saveFinish(trip, data());
        trip.startGenerating(1);
        store.save(trip); // RUNNING으로 저장된 채 서버가 꺼짐

        TripStore after = restarted();
        List<Trip> awaiting = after.findAwaitingGeneration();
        Trip loaded = awaiting.stream().filter(t -> t.getId().equals("t_db0002")).findFirst().orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(TripStatus.PENDING);
        assertThat(loaded.getGeneration()).isEqualTo(1);
        assertThat(loaded.getFinishData()).isEqualTo(data());
        assertThat(loaded.startGenerating(1)).as("같은 세대로 다시 생성 가능").isNotNull();
    }

    @Test
    void 같은_트립은_메모리에_객체_하나() {
        Trip trip = new Trip("t_db0003", DEST);
        store.insert(trip);
        TripStore after = restarted();
        assertThat(after.find("t_db0003").orElseThrow()).isSameAs(after.find("t_db0003").orElseThrow());
        assertThat(after.find("t_nope00")).isEmpty();
    }
}
