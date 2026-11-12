package com.randomdrive.server.dto;

/**
 * GET /api/v1/trips/{tripId}/recap 응답 바디.
 * DONE이 아니면 shareUrl/videoUrl/thumbnailUrl/summary는 null. DONE이어도 영상·썸네일을 못 만들었으면 그 필드는 null.
 */
public record RecapResponse(
        String tripId,
        TripStatus status,
        String shareUrl,
        String videoUrl,
        String thumbnailUrl,
        RecapSummaryDto summary
) {
}
