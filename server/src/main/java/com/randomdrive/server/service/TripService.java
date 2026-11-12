package com.randomdrive.server.service;

import com.randomdrive.server.dto.DestinationDto;
import com.randomdrive.server.dto.FinishTripRequest;
import com.randomdrive.server.dto.FinishTripResponse;
import com.randomdrive.server.dto.RandomDestinationRequest;
import com.randomdrive.server.dto.RandomDestinationResponse;
import com.randomdrive.server.dto.RecapResponse;
import com.randomdrive.server.dto.RecapSummaryDto;
import com.randomdrive.server.dto.SharePageView;
import com.randomdrive.server.dto.TripStatus;
import com.randomdrive.server.exception.NoCandidateException;
import com.randomdrive.server.exception.TripNotFoundException;
import com.randomdrive.server.recap.RecapExecutorConfig.RecapExecutor;
import com.randomdrive.server.recap.RecapMediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 비즈니스 로직 담당. 트립은 메모리(ConcurrentHashMap)에 보관.
 * 랜덤 목적지는 실제 추첨, 리캡(요약·영상·썸네일·공유 링크)은 finish 후 백그라운드 작업으로 만든다.
 * 컨트롤러는 HTTP만 신경 쓰고, "무엇을 할지"는 여기서 결정한다.
 */
@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);
    private static final String HEX = "0123456789abcdef";
    /** 공유 토큰 길이(바이트). 128비트 → URL-safe base64 22자. */
    private static final int SHARE_TOKEN_BYTES = 16;

    private final Map<String, Trip> trips = new ConcurrentHashMap<>();
    /** 공유 토큰 → 트립. 토큰은 생성 작업이 시작될 때 예약하고, 실패하면 지운다. */
    private final Map<String, Trip> tripsByShareToken = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final CandidateRepository candidateRepository;
    private final ShareProperties shareProperties;
    private final RecapMediaService media;
    private final RecapExecutor executor;
    private final Clock clock;

    public TripService(CandidateRepository candidateRepository, ShareProperties shareProperties,
                       RecapMediaService media, RecapExecutor executor, Clock clock) {
        this.candidateRepository = candidateRepository;
        this.shareProperties = shareProperties;
        this.media = media;
        this.executor = executor;
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
     * [2] 주행 종료. 처음이거나 FAILED였으면 리캡 생성 작업을 시작하고 PENDING을 준다.
     * 그 외 재전송은 새로 시작하지 않고 현재 상태를 반환(멱등).
     */
    public FinishTripResponse finishTrip(String tripId, FinishTripRequest request) {
        Trip trip = findTrip(tripId);
        Trip.FinishOutcome outcome = trip.finish(request);
        if (outcome.newGeneration() != null) {
            int gen = outcome.newGeneration();
            executor.submit(() -> generateRecap(trip, gen));
        }
        return new FinishTripResponse(trip.getId(), outcome.status());
    }

    /** [3] 리캡 조회. DONE이면 공유 링크·요약과, 만들어진 경우 영상·썸네일 주소를 준다. */
    public RecapResponse getRecap(String tripId) {
        Trip trip = findTrip(tripId);
        TripStatus status = trip.getStatus();
        Recap recap = trip.getRecap();
        if (status != TripStatus.DONE || recap == null) {
            return new RecapResponse(trip.getId(), status, null, null, null, null);
        }
        String token = recap.shareToken();
        return new RecapResponse(trip.getId(), status,
                shareProperties.shareUrl(token),
                recap.hasVideo() ? shareProperties.videoUrl(token) : null,
                recap.hasThumbnail() ? shareProperties.thumbnailUrl(token) : null,
                recap.summary());
    }

    /**
     * 리캡 생성 작업(백그라운드). 요약 계산 → 영상·썸네일 → DONE.
     * 이 사이에 FAILED 재시도로 세대가 바뀌었으면 결과를 버린다.
     */
    void generateRecap(Trip trip, int gen) {
        FinishTripRequest data = trip.startGenerating(gen);
        if (data == null) {
            return;
        }
        String token = reserveShareToken(trip);
        try {
            DestinationDto destination = trip.getDestination();
            RecapSummaryDto summary = RecapSummarizer.summarize(destination, data);
            List<StopDetector.Stop> stops = RecapSummarizer.unexpectedStops(destination, data);
            RecapMediaService.Media made = media.render(token, destination, imageLicense(destination), data, summary, stops);
            Recap recap = new Recap(token, summary, made.video(), made.thumbnail(), clock.instant());
            if (!trip.complete(gen, recap)) {
                discard(token);
            }
        } catch (Exception e) {
            log.error("Recap generation failed for {}", trip.getId(), e);
            discard(token);
            trip.fail(gen);
        }
    }

    /** 공유 페이지(/r/{token})·미디어 조회 결과. */
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
        if (recap == null || !recap.shareToken().equals(token)) {
            return new SharePageResult.NotFound();
        }
        if (recap.isExpired(clock.instant(), shareProperties.ttl())) {
            return new SharePageResult.Expired();
        }
        DestinationDto destination = trip.getDestination();
        return new SharePageResult.Found(SharePageView.of(destination, imageLicense(destination),
                trip.getFinishData().endedAt(), recap.summary(),
                recap.hasVideo() ? shareProperties.videoUrl(token) : null,
                recap.hasThumbnail() ? shareProperties.thumbnailUrl(token) : null,
                shareProperties.shareUrl(token)));
    }

    /** 공유 링크에 딸린 미디어 파일. */
    public enum MediaKind { VIDEO, THUMBNAIL }

    public sealed interface MediaResult {
        record Found(Path file) implements MediaResult {
        }

        record NotFound() implements MediaResult {
        }

        record Expired() implements MediaResult {
        }
    }

    /** 공유 페이지와 같은 규칙(없는 토큰 404, 기간 지남 410)으로 영상·썸네일 파일을 찾는다. */
    public MediaResult findMedia(String token, MediaKind kind) {
        Trip trip = token == null ? null : tripsByShareToken.get(token);
        Recap recap = trip == null ? null : trip.getRecap();
        if (recap == null || !recap.shareToken().equals(token)) {
            return new MediaResult.NotFound();
        }
        if (recap.isExpired(clock.instant(), shareProperties.ttl())) {
            return new MediaResult.Expired();
        }
        boolean has = kind == MediaKind.VIDEO ? recap.hasVideo() : recap.hasThumbnail();
        Path file = kind == MediaKind.VIDEO ? media.videoFile(token) : media.thumbnailFile(token);
        return has && Files.isRegularFile(file) ? new MediaResult.Found(file) : new MediaResult.NotFound();
    }

    private String imageLicense(DestinationDto destination) {
        return candidateRepository.findById(destination.placeId()).map(Candidate::imageLicense).orElse(null);
    }

    /** 추측할 수 없는 공유 토큰을 뽑아 이 트립에 예약한다. 혹시 겹치면 다시 뽑는다. */
    private String reserveShareToken(Trip trip) {
        while (true) {
            byte[] bytes = new byte[SHARE_TOKEN_BYTES];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (tripsByShareToken.putIfAbsent(token, trip) == null) {
                return token;
            }
        }
    }

    private void discard(String token) {
        tripsByShareToken.remove(token);
        media.delete(token);
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
