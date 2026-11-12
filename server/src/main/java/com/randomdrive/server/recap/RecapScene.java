package com.randomdrive.server.recap;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 영상·썸네일 한 편을 그리는 데 필요한 값. 위치 좌표는 이미 출발지 주변을 가린 뒤의 것이다(RouteMask).
 *
 * @param placeName          목적지 이름
 * @param originName         출발지 동네 이름(예: "용산구 동자동"). 모르면 null
 * @param dateText           "2026년 11월 12일"
 * @param distanceText       "123.4km"
 * @param durationText       "2시간 30분"
 * @param stopCount          예상 밖 정차 횟수(요약 수치와 같은 값)
 * @param arrived            목적지 도착 여부(DESTINATION_ARRIVED 이벤트 유무)
 * @param photo              목적지 사진. 없으면 null
 * @param photoNoDerivatives 공공누리 3유형(변경 금지) — 자르거나 위에 글자를 얹지 않는다
 * @param route              보여도 되는 경로 조각들. 각 점은 {lat, lng}
 * @param stops              보여도 되는 정차 위치들 {lat, lng}
 * @param destination        목적지 {lat, lng}
 */
public record RecapScene(
        String placeName,
        String originName,
        String dateText,
        String distanceText,
        String durationText,
        int stopCount,
        boolean arrived,
        BufferedImage photo,
        boolean photoNoDerivatives,
        List<List<double[]>> route,
        List<double[]> stops,
        double[] destination
) {
    /** "용산구 동자동 → 남한산성", 출발지를 모르면 목적지 이름만. */
    public String journeyText() {
        return originName == null ? placeName : originName + " → " + placeName;
    }
}
