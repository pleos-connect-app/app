package com.randomdrive.server.service;

/**
 * 랜덤 목적지 후보 장소 한 곳 (candidates.json의 한 줄).
 *
 * @param imageLicense 대표 이미지의 공공누리 유형(TourAPI cpyrhtDivCd: Type1 출처표시, Type3 출처표시+변경금지).
 *                     API 응답에는 나가지 않고, 영상·공유 페이지에서 이미지를 쓸 때 판단용. 없으면 null.
 * @param active       false면 폐업·출입 제한 등으로 추첨에서 뺀다. JSON에서 생략하면 true.
 */
public record Candidate(
        String placeId,
        String name,
        double lat,
        double lng,
        String address,
        String imageUrl,
        String imageLicense,
        Boolean active
) {
    public Candidate {
        if (active == null) {
            active = true;
        }
    }

    /** 이 후보가 기준 좌표에서 떨어진 직선거리, km. */
    public double distanceKmFrom(double lat, double lng) {
        return Geo.distanceKm(lat, lng, this.lat, this.lng);
    }
}
