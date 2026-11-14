package com.randomdrive.server.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 앱 API(/api/v1/**) 보호 설정.
 *
 * @param keys      앱이 X-App-Key 헤더로 보내야 하는 키 목록(여러 개면 교체 기간에 옛 키·새 키 둘 다 허용).
 *                  비어 있으면 키 검사를 하지 않는다(로컬 개발·앱이 헤더를 붙이기 전). 환경변수 API_KEYS(쉼표 구분).
 * @param rateLimit IP당 요청 수 제한
 */
@ConfigurationProperties("api")
public record ApiGuardProperties(
        @DefaultValue List<String> keys,
        @DefaultValue RateLimit rateLimit
) {
    public ApiGuardProperties {
        keys = keys.stream().map(String::trim).filter(k -> !k.isEmpty()).toList();
    }

    /**
     * @param enabled               false면 제한하지 않는다(테스트용)
     * @param destinationsPerMinute 랜덤 목적지 추첨
     * @param finishesPerHour       주행 종료(영상 생성을 일으키는 무거운 요청)
     * @param recapsPerMinute       리캡 조회(앱이 2초마다 최대 3분 조회)
     * @param othersPerMinute       그 밖의 /api 요청
     * @param tilesPerMinute        앱 위성 지도 타일 중계(추첨 화면 한 번에 수십~백여 장)
     * @param geosPerMinute         좌표 → 지역 이름(앱은 1km 이상 움직였을 때만 묻는다)
     */
    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("30") int destinationsPerMinute,
            @DefaultValue("30") int finishesPerHour,
            @DefaultValue("120") int recapsPerMinute,
            @DefaultValue("60") int othersPerMinute,
            @DefaultValue("600") int tilesPerMinute,
            @DefaultValue("30") int geosPerMinute
    ) {
    }
}
