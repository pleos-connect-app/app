package com.randomdrive.server.dto;

import jakarta.validation.constraints.NotNull;

/** 주행 중 이벤트 하나. name은 선택(예: 경유지 이름). */
public record EventDto(
        @NotNull EventType type,
        @NotNull Long ts,
        @NotNull Double lat,
        @NotNull Double lng,
        String name
) {
}
