package com.randomdrive.server.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/v1/trips/{tripId}/finish 요청 바디.
 * points는 2~10,000개. events는 빠지면 빈 리스트로 취급.
 * 리스트 원소 앞의 @Valid는 "리스트 안의 각 객체도 검증하라"는 뜻.
 */
public record FinishTripRequest(
        @NotNull Long startedAt,
        @NotNull Long endedAt,
        @NotNull @Size(min = 2, max = 10_000) List<@Valid @NotNull PointDto> points,
        List<@Valid @NotNull EventDto> events
) {
    public FinishTripRequest {
        if (events == null) {
            events = List.of();
        }
    }
}
