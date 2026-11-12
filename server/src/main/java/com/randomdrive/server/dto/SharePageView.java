package com.randomdrive.server.dto;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 공유 페이지(/r/{token}) 화면에 넣을 값. 숫자는 화면용 문자열로 미리 바꿔 둔다.
 * 개인정보·차량 식별 정보·원본 위치 로그(출발지, 경로 좌표)는 넣지 않는다(F-08).
 *
 * @param imageNoDerivatives 대표 이미지가 공공누리 3유형(변경 금지)인지. 화면에서 자르지 않고 원본 비율로 보여 준다.
 * @param videoUrl           리캡 영상 주소. 없으면 null("준비 중")
 * @param thumbnailUrl       리캡 썸네일(16:9) 주소. 영상 포스터·미리보기 이미지. 없으면 null
 */
public record SharePageView(
        String placeName,
        String address,
        String imageUrl,
        boolean imageNoDerivatives,
        String dateText,
        String distanceText,
        String durationText,
        int stopCount,
        String videoUrl,
        String thumbnailUrl,
        String shareUrl
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN);

    public static SharePageView of(DestinationDto destination, String imageLicense, long endedAtMillis,
                                   RecapSummaryDto summary, String videoUrl, String thumbnailUrl, String shareUrl) {
        return new SharePageView(
                destination.name(),
                destination.address(),
                destination.imageUrl(),
                "Type3".equals(imageLicense),
                formatDate(endedAtMillis),
                formatKm(summary.distanceKm()),
                formatMinutes(summary.durationMin()),
                summary.stopCount(),
                videoUrl,
                thumbnailUrl,
                shareUrl);
    }

    /** 한국 시간 기준 "2026년 11월 12일". */
    public static String formatDate(long epochMillis) {
        return DATE.format(Instant.ofEpochMilli(epochMillis).atZone(KST));
    }

    public static String formatKm(double km) {
        return String.format(Locale.ROOT, "%.1fkm", km);
    }

    public static String formatMinutes(int minutes) {
        if (minutes < 60) {
            return minutes + "분";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        return m == 0 ? h + "시간" : h + "시간 " + m + "분";
    }
}
