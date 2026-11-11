package com.randomdrive.server.service;

import com.randomdrive.server.dto.DestinationDto;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * 반경 기반 랜덤 목적지 추첨 (MVP F-01). 앱의 CandidatePicker와 같은 규칙.
 * - 반경은 요청 좌표에서의 직선거리 기준 (경계값 포함)
 * - 직선 MIN_KM 미만은 "너무 가까운 목적지"라 제외
 * - active=false(폐업·출입 제한) 제외
 * 상태가 없는 순수 로직이라 난수 생성기를 밖에서 받는다(테스트에서 결과를 고정하기 위해).
 */
public final class DestinationPicker {

    /** 앱 CandidatePicker.MIN_KM과 같은 값. */
    public static final double MIN_KM = 3.0;

    private DestinationPicker() {
    }

    /** 조건에 맞는 후보 중 하나를 균등 확률로 뽑는다. 없으면 empty. */
    public static Optional<DestinationDto> pick(List<Candidate> candidates, double lat, double lng,
                                                int radiusKm, RandomGenerator random) {
        List<Candidate> pool = candidates.stream()
                .filter(Candidate::active)
                .filter(c -> {
                    double km = c.distanceKmFrom(lat, lng);
                    return km >= MIN_KM && km <= radiusKm;
                })
                .toList();
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        Candidate chosen = pool.get(random.nextInt(pool.size()));
        return Optional.of(toDestination(chosen, lat, lng));
    }

    /** 후보를 응답 DTO로 바꾼다. distanceKm는 요청 좌표부터의 직선거리, 소수 첫째 자리 반올림. */
    public static DestinationDto toDestination(Candidate c, double lat, double lng) {
        double km = Math.round(c.distanceKmFrom(lat, lng) * 10) / 10.0;
        return new DestinationDto(c.placeId(), c.name(), c.lat(), c.lng(), c.address(), c.imageUrl(), km);
    }
}
