package com.randomdrive.server.dto;

/** GET /api/v1/trips/{tripId}/recap 응답 바디. DONE이 아니면 shareUrl/videoUrl/summary는 null. */
public record RecapResponse(
        String tripId,
        TripStatus status,
        String shareUrl,
        String videoUrl,
        RecapSummaryDto summary
) {
}
