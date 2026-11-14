package com.gyeongroital.gallaemallae.data

import com.gyeongroital.gallaemallae.domain.Destination
import com.gyeongroital.gallaemallae.domain.Recap
import com.gyeongroital.gallaemallae.domain.TrackPoint
import com.gyeongroital.gallaemallae.domain.TripEvent

/** 랜덤 목적지 + 서버가 발급한 tripId. */
data class RandomDestinationResult(val tripId: String, val destination: Destination)

data class FinishTripRequest(
    val startedAt: Long,
    val endedAt: Long,
    val points: List<TrackPoint>,
    val events: List<TripEvent>,
)

/**
 * 앱 ↔ 서버 계약 (server/CLAUDE.md 기준).
 * 1. POST /api/v1/destinations/random
 * 2. POST /api/v1/trips/{tripId}/finish  → 202
 * 3. GET  /api/v1/trips/{tripId}/recap
 */
interface RecapApi {
    suspend fun randomDestination(lat: Double, lng: Double, radiusKm: Int, demo: Boolean, excludePlaceIds: Set<String> = emptySet()): RandomDestinationResult
    suspend fun finishTrip(tripId: String, request: FinishTripRequest): RecapStatusResult
    suspend fun getRecap(tripId: String): Recap
}

data class RecapStatusResult(val tripId: String, val status: com.gyeongroital.gallaemallae.domain.RecapStatus)

/** 서버 오류. code는 서버의 ErrorResponse.code(INVALID_REQUEST, TRIP_NOT_FOUND 등) 또는 앱 내부 코드. */
class ApiException(val code: String, message: String, val httpStatus: Int? = null, cause: Throwable? = null) :
    Exception(message, cause) {
    /** 네트워크 단절·타임아웃·5xx처럼 다시 시도해 볼 만한 오류. */
    val retryable: Boolean get() = code == CODE_NETWORK || (httpStatus != null && httpStatus >= 500)

    companion object {
        const val CODE_NETWORK = "NETWORK"
        const val CODE_NO_CANDIDATE = "NO_CANDIDATE"
        const val CODE_BAD_RESPONSE = "BAD_RESPONSE"
    }
}
