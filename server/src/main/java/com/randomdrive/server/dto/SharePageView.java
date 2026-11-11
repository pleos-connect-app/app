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
        String shareUrl
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN);

    public static SharePageView of(DestinationDto destination, String imageLicense, long endedAtMillis,
                                   RecapSummaryDto summary, String videoUrl, String shareUrl) {
        return new SharePageView(
                destination.name(),
                destination.address(),
                destination.imageUrl(),
                "Type3".equals(imageLicense),
                DATE.format(Instant.ofEpochMilli(endedAtMillis).atZone(KST)),
                formatKm(summary.distanceKm()),
                formatMinutes(summary.durationMin()),
                summary.stopCount(),
                videoUrl,
                shareUrl);
    }

    static String formatKm(double km) {
        return String.format(Locale.ROOT, "%.1fkm", km);
    }

    static String formatMinutes(int minutes) {
        if (minutes < 60) {
            return minutes + "분";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        return m == 0 ? h + "시간" : h + "시간 " + m + "분";
    }
}
