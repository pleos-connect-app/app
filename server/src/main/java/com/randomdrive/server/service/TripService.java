package com.randomdrive.server.service;

import com.randomdrive.server.dto.DestinationDto;
import com.randomdrive.server.dto.FinishTripRequest;
import com.randomdrive.server.dto.FinishTripResponse;
import com.randomdrive.server.dto.RandomDestinationRequest;
import com.randomdrive.server.dto.RandomDestinationResponse;
import com.randomdrive.server.dto.RecapResponse;
import com.randomdrive.server.dto.RecapSummaryDto;
import com.randomdrive.server.dto.TripStatus;
import com.randomdrive.server.exception.TripNotFoundException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 비즈니스 로직 담당. 지금은 mock이라 고정 응답을 돌려주고, 트립은 메모리(ConcurrentHashMap)에 보관.
 * 컨트롤러는 HTTP만 신경 쓰고, "무엇을 할지"는 여기서 결정한다.
 */
@Service
public class TripService {

    private static final DestinationDto MOCK_DESTINATION = new DestinationDto(
            "126508", "안목해변", 37.7718, 128.9479,
            "강원특별자치도 강릉시 창해로14번길", null, 92.4);

    private static final String MOCK_SHARE_URL = "http://localhost:8080/r/demo123";
    private static final String MOCK_VIDEO_URL = "http://localhost:8080/videos/sample.mp4";
    private static final RecapSummaryDto MOCK_SUMMARY = new RecapSummaryDto(214.6, 150, 2);

    private static final String HEX = "0123456789abcdef";

    private final Map<String, Trip> trips = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** [1] 랜덤 목적지 추천. mock: 요청값과 무관하게 항상 안목해변, tripId만 새로 발급. */
    public RandomDestinationResponse createRandomDestination(RandomDestinationRequest request) {
        Trip trip = createTrip();
        return new RandomDestinationResponse(trip.getId(), MOCK_DESTINATION);
    }

    /** [2] 주행 종료. 같은 tripId로 다시 호출되면 새로 시작하지 않고 현재 상태를 반환. */
    public FinishTripResponse finishTrip(String tripId, FinishTripRequest request) {
        Trip trip = findTrip(tripId);
        TripStatus status = trip.finish();
        return new FinishTripResponse(trip.getId(), status);
    }

    /** [3] 리캡 조회. finish 후 1~2번째는 RUNNING, 3번째부터 DONE. */
    public RecapResponse getRecap(String tripId) {
        Trip trip = findTrip(tripId);
        TripStatus status = trip.pollRecap();
        if (status == TripStatus.DONE) {
            return new RecapResponse(trip.getId(), status, MOCK_SHARE_URL, MOCK_VIDEO_URL, MOCK_SUMMARY);
        }
        return new RecapResponse(trip.getId(), status, null, null, null);
    }

    private Trip findTrip(String tripId) {
        Trip trip = trips.get(tripId);
        if (trip == null) {
            throw new TripNotFoundException(tripId);
        }
        return trip;
    }

    /** "t_" + 랜덤 16진수 6자리. 혹시 이미 있는 id면 다시 뽑는다. */
    private Trip createTrip() {
        while (true) {
            String id = "t_" + randomHex(6);
            Trip trip = new Trip(id);
            if (trips.putIfAbsent(id, trip) == null) {
                return trip;
            }
        }
    }

    private String randomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(HEX.charAt(random.nextInt(HEX.length())));
        }
        return sb.toString();
    }
}
