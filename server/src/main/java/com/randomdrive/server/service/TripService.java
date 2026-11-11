package com.randomdrive.server.service;

import com.randomdrive.server.dto.DestinationDto;
import com.randomdrive.server.dto.FinishTripRequest;
import com.randomdrive.server.dto.FinishTripResponse;
import com.randomdrive.server.dto.RandomDestinationRequest;
import com.randomdrive.server.dto.RandomDestinationResponse;
import com.randomdrive.server.dto.RecapResponse;
import com.randomdrive.server.dto.SharePageView;
import com.randomdrive.server.dto.TripStatus;
import com.randomdrive.server.exception.NoCandidateException;
import com.randomdrive.server.exception.TripNotFoundException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 비즈니스 로직 담당. 트립은 메모리(ConcurrentHashMap)에 보관.
 * 랜덤 목적지는 실제 추첨, 리캡 요약·공유 링크는 실제 값, 영상 생성 진행(RUNNING→DONE)만 아직 mock.
 * 컨트롤러는 HTTP만 신경 쓰고, "무엇을 할지"는 여기서 결정한다.
 */
@Service
public class TripService {

    private static final String HEX = "0123456789abcdef";
    /** 공유 토큰 길이(바이트). 128비트 → URL-safe base64 22자. */
    private static final int SHARE_TOKEN_BYTES = 16;

    private final Map<String, Trip> trips = new ConcurrentHashMap<>();
    /** 공유 토큰 → 트립. 토큰은 리캡이 DONE이 될 때 등록된다. */
    private final Map<String, Trip> tripsByShareToken = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final CandidateRepository candidateRepository;
    private final ShareProperties shareProperties;
    private final Clock clock;

    public TripService(CandidateRepository candidateRepository, ShareProperties shareProperties, Clock clock) {
        this.candidateRepository = candidateRepository;
        this.shareProperties = shareProperties;
        this.clock = clock;
    }

    /**
     * [1] 랜덤 목적지 추천 (F-01).
     * - demo=true: 반경과 무관하게 항상 시연용 장소(남한산성)
     * - 그 외: 직선 3km 이상 radiusKm 이하, active인 후보 중 랜덤 1곳
     * 후보가 없으면 404 NO_CANDIDATE이고, 이때는 트립을 만들지 않는다.
     */
    public RandomDestinationResponse createRandomDestination(RandomDestinationRequest request) {
        double lat = request.lat();
        double lng = request.lng();
        DestinationDto destination = request.demo()
                ? DestinationPicker.toDestination(candidateRepository.demo(), lat, lng)
                : DestinationPicker.pick(candidateRepository.findAll(), lat, lng, request.radiusKm(),
                                ThreadLocalRandom.current())
                        .orElseThrow(() -> new NoCandidateException(request.radiusKm()));
        Trip trip = createTrip(destination);
        return new RandomDestinationResponse(trip.getId(), destination);
    }

    /**
     * [2] 주행 종료. 같은 tripId로 다시 호출되면 새로 시작하지 않고 현재 상태를 반환.
     * 단, 리캡 생성이 FAILED였으면 새 데이터로 다시 시작한다(앱 "다시 시도").
     */
    public FinishTripResponse finishTrip(String tripId, FinishTripRequest request) {
        Trip trip = findTrip(tripId);
        TripStatus status = trip.finish(request);
        return new FinishTripResponse(trip.getId(), status);
    }

    /**
     * [3] 리캡 조회. (mock 진행) finish 후 1~2번째는 RUNNING, 3번째부터 DONE.
     * 처음 DONE이 될 때 요약을 계산하고 공유 토큰을 발급한다.
     */
    public RecapResponse getRecap(String tripId) {
        Trip trip = findTrip(tripId);
        TripStatus status = trip.pollRecap();
        if (status == TripStatus.DONE) {
            Recap recap = trip.completeRecap(this::buildRecap);
            tripsByShareToken.putIfAbsent(recap.shareToken(), trip);
            return new RecapResponse(trip.getId(), status,
                    shareProperties.shareUrl(recap.shareToken()), recap.videoUrl(), recap.summary());
        }
        return new RecapResponse(trip.getId(), status, null, null, null);
    }

    /** 공유 페이지(/r/{token}) 조회 결과. */
    public sealed interface SharePageResult {
        record Found(SharePageView view) implements SharePageResult {
        }

        record NotFound() implements SharePageResult {
        }

        record Expired() implements SharePageResult {
        }
    }

    public SharePageResult findSharePage(String token) {
        Trip trip = token == null ? null : tripsByShareToken.get(token);
        Recap recap = trip == null ? null : trip.getRecap();
        if (recap == null) {
            return new SharePageResult.NotFound();
        }
        if (recap.isExpired(clock.instant(), shareProperties.ttl())) {
            return new SharePageResult.Expired();
        }
        DestinationDto destination = trip.getDestination();
        String imageLicense = candidateRepository.findById(destination.placeId())
                .map(Candidate::imageLicense).orElse(null);
        return new SharePageResult.Found(SharePageView.of(destination, imageLicense,
                trip.getFinishData().endedAt(), recap.summary(), recap.videoUrl(), shareProperties.shareUrl(token)));
    }

    /** Trip.completeRecap 안(트립 잠금 상태)에서 한 번만 불린다. */
    private Recap buildRecap(Trip trip) {
        return new Recap(
                newShareToken(),
                RecapSummarizer.summarize(trip.getDestination(), trip.getFinishData()),
                null, // 영상 생성(F-07) 전까지 영상 없음
                clock.instant());
    }

    /** 추측할 수 없는 공유 토큰. 혹시 겹치면 다시 뽑는다. */
    private String newShareToken() {
        while (true) {
            byte[] bytes = new byte[SHARE_TOKEN_BYTES];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (!tripsByShareToken.containsKey(token)) {
                return token;
            }
        }
    }

    private Trip findTrip(String tripId) {
        Trip trip = trips.get(tripId);
        if (trip == null) {
            throw new TripNotFoundException(tripId);
        }
        return trip;
    }

    /** "t_" + 랜덤 16진수 6자리. 혹시 이미 있는 id면 다시 뽑는다. */
    private Trip createTrip(DestinationDto destination) {
        while (true) {
            String id = "t_" + randomHex(6);
            Trip trip = new Trip(id, destination);
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
