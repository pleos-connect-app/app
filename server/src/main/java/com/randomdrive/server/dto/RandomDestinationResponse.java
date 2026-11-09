package com.randomdrive.server.dto;

/** POST /api/v1/destinations/random 응답 바디. */
public record RandomDestinationResponse(
        String tripId,
        DestinationDto destination
) {
}
