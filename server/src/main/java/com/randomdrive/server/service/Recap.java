package com.randomdrive.server.service;

import com.randomdrive.server.dto.RecapSummaryDto;

import java.time.Duration;
import java.time.Instant;

/**
 * 완료된 리캡 결과. 트립이 처음 DONE이 될 때 한 번 만들어지고 바뀌지 않는다.
 *
 * @param shareToken  공유 링크 토큰(128비트 난수, URL-safe base64 22자). 추측할 수 없어야 한다.
 * @param summary     요약 수치
 * @param videoUrl    영상 주소. 영상 생성(F-07) 전에는 null.
 * @param completedAt 완료 시각. 공유 링크 만료 기준.
 */
public record Recap(String shareToken, RecapSummaryDto summary, String videoUrl, Instant completedAt) {

    public boolean isExpired(Instant now, Duration ttl) {
        return !now.isBefore(completedAt.plus(ttl));
    }
}
