package com.randomdrive.server.dto;

/** 추천 목적지 정보. imageUrl은 없을 수 있어 null 허용. */
public record DestinationDto(
        String placeId,
        String name,
        double lat,
        double lng,
        String address,
        String imageUrl,
        double distanceKm
) {
}
