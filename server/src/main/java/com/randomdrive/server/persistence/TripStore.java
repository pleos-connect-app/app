package com.randomdrive.server.persistence;

import com.randomdrive.server.dto.DestinationDto;
import com.randomdrive.server.dto.FinishTripRequest;
import com.randomdrive.server.dto.RecapSummaryDto;
import com.randomdrive.server.dto.TripStatus;
import com.randomdrive.server.service.Recap;
import com.randomdrive.server.service.Trip;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 트립 저장소(H2 파일 DB). 서버가 한 대라 트립 하나는 메모리에 객체 하나만 둔다(같은 객체로 잠금을 걸어야
 * 상태 규칙이 지켜지므로). 처음 찾을 때 DB에서 읽어 캐시하고, 상태를 바꾼 쪽이 save를 부른다.
 * DONE 트립은 무거운 주행 데이터를 메모리에서 내려놓으므로 캐시에 남아도 작다.
 */
@Component
public class TripStore {

    private static final List<TripStatus> UNFINISHED = List.of(TripStatus.PENDING, TripStatus.RUNNING);

    private final TripJpaRepository trips;
    private final TripFinishJpaRepository finishes;
    private final JsonMapper json;
    private final Clock clock;
    private final Map<String, Trip> cache = new ConcurrentHashMap<>();

    public TripStore(TripJpaRepository trips, TripFinishJpaRepository finishes, Clock clock) {
        this.trips = trips;
        this.finishes = finishes;
        this.json = JsonMapper.builder().build();
        this.clock = clock;
    }

    /** 새 트립 저장. 이미 있는 id면 false(호출 측이 다른 id로 다시). */
    @Transactional
    public boolean insert(Trip trip) {
        if (cache.containsKey(trip.getId()) || trips.existsById(trip.getId())) {
            return false;
        }
        TripEntity e = new TripEntity(trip.getId(), clock.instant());
        apply(e, trip.snapshot());
        trips.save(e);
        cache.put(trip.getId(), trip);
        return true;
    }

    public Optional<Trip> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Trip cached = cache.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        return trips.findById(id).map(e -> cache.computeIfAbsent(id, k -> restore(e)));
    }

    public Optional<Trip> findByShareToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return trips.findByShareToken(token).flatMap(e -> find(e.getId()));
    }

    public boolean shareTokenExists(String token) {
        return trips.existsByShareToken(token);
    }

    /** 트립 상태 저장. 호출 측은 트립 잠금(synchronized(trip)) 안에서 불러 저장 순서가 뒤바뀌지 않게 한다. */
    @Transactional
    public void save(Trip trip) {
        TripEntity e = trips.findById(trip.getId()).orElseGet(() -> new TripEntity(trip.getId(), clock.instant()));
        apply(e, trip.snapshot());
        trips.save(e);
    }

    /** finish로 새 주행 데이터를 받았을 때: 주행 데이터와 상태를 함께 저장. */
    @Transactional
    public void saveFinish(Trip trip, FinishTripRequest data) {
        finishes.save(new TripFinishEntity(trip.getId(), json.writeValueAsString(data)));
        save(trip);
    }

    /** finish 후 생성을 기다리던 트립들(서버 재시작 전에 끝나지 못한 것). 시작할 때 다시 생성한다. */
    public List<Trip> findAwaitingGeneration() {
        return trips.findByFinishedTrueAndStatusIn(UNFINISHED).stream()
                .map(e -> find(e.getId()).orElse(null))
                .filter(t -> t != null && t.awaitsGeneration())
                .toList();
    }

    private Trip restore(TripEntity e) {
        DestinationDto destination = new DestinationDto(e.getPlaceId(), e.getPlaceName(), e.getPlaceLat(),
                e.getPlaceLng(), e.getPlaceAddress(), e.getPlaceImageUrl(), e.getPlaceDistanceKm());
        Recap recap = e.getStatus() == TripStatus.DONE && e.getShareToken() != null
                ? new Recap(e.getShareToken(),
                new RecapSummaryDto(e.getSummaryDistanceKm(), e.getSummaryDurationMin(), e.getSummaryStopCount()),
                e.isHasVideo(), e.isHasThumbnail(), e.getCompletedAt())
                : null;
        // 주행 데이터는 다시 생성해야 하는 트립만 읽는다(DONE·FAILED는 필요 없음).
        FinishTripRequest data = e.isFinished() && UNFINISHED.contains(e.getStatus())
                ? finishes.findById(e.getId()).map(f -> json.readValue(f.getJson(), FinishTripRequest.class)).orElse(null)
                : null;
        return Trip.restore(e.getId(), destination, e.getStatus(), e.getGeneration(), e.isFinished(),
                e.getEndedAt(), data, recap);
    }

    private static void apply(TripEntity e, Trip.Snapshot s) {
        DestinationDto d = s.destination();
        e.setPlace(d.placeId(), d.name(), d.lat(), d.lng(), d.address(), d.imageUrl(), d.distanceKm());
        e.setState(s.status(), s.generation(), s.finished(), s.endedAt());
        Recap r = s.recap();
        if (r == null) {
            e.setRecap(null, null, null, null, false, false, null);
        } else {
            RecapSummaryDto sum = r.summary();
            e.setRecap(r.shareToken(), sum.distanceKm(), sum.durationMin(), sum.stopCount(),
                    r.hasVideo(), r.hasThumbnail(), r.completedAt());
        }
    }
}
