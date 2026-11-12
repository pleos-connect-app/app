package com.randomdrive.server.service;

import com.randomdrive.server.dto.RecapSummaryDto;

import java.time.Duration;
import java.time.Instant;

/**
 * 완료된 리캡 결과. 생성 작업이 끝날 때 한 번 만들어지고 바뀌지 않는다.
 *
 * @param shareToken   공유 링크 토큰(128비트 난수, URL-safe base64 22자). 추측할 수 없어야 한다.
 *                     영상·썸네일 파일 주소(/r/{token}/...)에도 쓴다.
 * @param summary      요약 수치
 * @param hasVideo     영상 파일이 있는지(ffmpeg가 없거나 꺼져 있으면 false)
 * @param hasThumbnail 썸네일 파일이 있는지
 * @param completedAt  완료 시각. 공유 링크·미디어 만료 기준.
 */
public record Recap(String shareToken, RecapSummaryDto summary, boolean hasVideo, boolean hasThumbnail,
                    Instant completedAt) {

    public boolean isExpired(Instant now, Duration ttl) {
        return !now.isBefore(completedAt.plus(ttl));
    }
}
