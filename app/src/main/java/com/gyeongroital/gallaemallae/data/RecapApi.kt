package com.gyeongroital.gallaemallae.data

import com.gyeongroital.gallaemallae.domain.Destination
import com.gyeongroital.gallaemallae.domain.Recap
import com.gyeongroital.gallaemallae.domain.TrackPoint
import com.gyeongroital.gallaemallae.domain.TripEvent

/** 좌표의 행정구역 이름. [full]="서울특별시 용산구 동자동", [short]="용산구 동자동". */
data class AreaName(val full: String, val short: String)

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
 * 4. GET  /api/v1/geo/area?lat=&lng=          (좌표 → 시·구·동, VWorld 키는 서버에만)
 *    GET  /api/v1/map/satellite/{z}/{x}/{y}.jpeg (위성 타일 — [com.gyeongroital.gallaemallae.ui.SatelliteTiles]가 직접 받음)
 */
interface RecapApi {
    suspend fun randomDestination(lat: Double, lng: Double, radiusKm: Int, demo: Boolean, excludePlaceIds: Set<String> = emptySet()): RandomDestinationResult
    suspend fun finishTrip(tripId: String, request: FinishTripRequest): RecapStatusResult
    suspend fun getRecap(tripId: String): Recap

    /** 좌표의 시·구·동 이름. 못 찾거나 실패하면 null(화면은 "현재 위치"). 예외를 던지지 않는다. */
    suspend fun areaName(lat: Double, lng: Double): AreaName?
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
