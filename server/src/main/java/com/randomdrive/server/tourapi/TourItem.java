package com.randomdrive.server.tourapi;

/**
 * TourAPI areaBasedList2 응답의 item 한 건 중 우리가 쓰는 필드만.
 * TourAPI는 숫자도 문자열로 주므로(mapx="127.09...") 원문 그대로 보관하고 변환은 CandidateImporter가 한다.
 *
 * @param mapx         경도(WGS84)
 * @param mapy         위도(WGS84)
 * @param cpyrhtDivCd  대표 이미지 공공누리 유형(Type1: 출처표시, Type3: 출처표시+변경금지)
 * @param lclsSystm1   새 분류체계 대분류(예: NA 자연관광)
 * @param lclsSystm2   새 분류체계 중분류(예: VE03 도시공원)
 */
public record TourItem(
        String contentId,
        String title,
        String addr1,
        String mapx,
        String mapy,
        String firstImage,
        String cpyrhtDivCd,
        String lclsSystm1,
        String lclsSystm2
) {
}
