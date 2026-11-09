package com.randomdrive.server.dto;

/** POST /api/v1/trips/{tripId}/finish 응답 바디 (HTTP 202). */
public record FinishTripResponse(
        String tripId,
        TripStatus status
) {
}
