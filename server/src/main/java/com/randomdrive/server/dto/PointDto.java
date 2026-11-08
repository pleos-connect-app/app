package com.randomdrive.server.dto;

import jakarta.validation.constraints.NotNull;

/** 주행 경로의 GPS 점 하나. ts는 epoch 밀리초. */
public record PointDto(
        @NotNull Double lat,
        @NotNull Double lng,
        @NotNull Long ts
) {
}
