package com.randomdrive.server.dto;

/** 리캡 요약 수치. */
public record RecapSummaryDto(
        double distanceKm,
        int durationMin,
        int stopCount
) {
}
