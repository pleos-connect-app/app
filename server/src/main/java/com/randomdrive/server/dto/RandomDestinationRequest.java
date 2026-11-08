package com.randomdrive.server.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** POST /api/v1/destinations/random 요청 바디. */
public record RandomDestinationRequest(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
        @NotNull Integer radiusKm,
        Boolean demo
) {
    private static final Set<Integer> ALLOWED_RADIUS_KM = Set.of(50, 100, 150);

    /** compact constructor: demo가 빠지면(null) 기본값 false로 채운다. */
    public RandomDestinationRequest {
        if (demo == null) {
            demo = false;
        }
    }

    /** radiusKm 허용값 검사. @AssertTrue는 이 메서드가 false를 반환하면 검증 실패로 처리한다. */
    @AssertTrue(message = "radiusKm must be one of 50, 100, 150")
    public boolean isRadiusKmAllowed() {
        return radiusKm == null || ALLOWED_RADIUS_KM.contains(radiusKm);
    }
}
