package com.gyeongroital.gallaemallae.data

import com.gyeongroital.gallaemallae.domain.Candidate
import com.gyeongroital.gallaemallae.domain.CandidatePicker
import com.gyeongroital.gallaemallae.domain.Geo
import com.gyeongroital.gallaemallae.domain.LatLng
import com.gyeongroital.gallaemallae.domain.Recap
import com.gyeongroital.gallaemallae.domain.RecapStatus
import com.gyeongroital.gallaemallae.domain.RecapSummary
import com.gyeongroital.gallaemallae.domain.StopDetector
import com.gyeongroital.gallaemallae.domain.TripEventType
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 서버 없이 앱 전체 흐름을 돌리기 위한 가짜 서버. 동작은 server/의 mock 계약을 그대로 따른다.
 * - 목적지: 앱 assets의 후보 목록에서 반경 내 랜덤 추첨 (demo=true면 남한산성 고정)
 * - finish 후 리캡 조회 1~2번째 RUNNING, 3번째부터 DONE
 * - 요약값은 업로드된 경로로 실제 계산. 공유 URL은 열리지 않는 자리표시자(.invalid) — 실제 공유 페이지는 서버 범위.
 */
class FakeRecapApi(
    private val candidates: () -> List<Candidate>,
    private val picker: CandidatePicker = CandidatePicker(),
    private val latencyMs: Long = 400,
) : RecapApi {

    private class FakeTrip(val destination: LatLng) {
        var finished = false
        var polls = 0
        var summary: RecapSummary? = null
    }

    private val trips = ConcurrentHashMap<String, FakeTrip>()

    override suspend fun randomDestination(lat: Double, lng: Double, radiusKm: Int, demo: Boolean, excludePlaceIds: Set<String>): RandomDestinationResult {
        delay(latencyMs)
        if (radiusKm !in CandidatePicker.RADIUS_OPTIONS_KM) {
            throw ApiException("INVALID_REQUEST", "radiusKm must be one of 50, 100, 150", 400)
        }
        val origin = LatLng(lat, lng)
        val all = candidates()
        val dest = if (demo) {
            all.firstOrNull { it.placeId == CandidatePicker.DEMO_PLACE_ID }?.let {
                com.gyeongroital.gallaemallae.domain.Destination(it.placeId, it.name, it.lat, it.lng, it.address, null,
                    Geo.distanceMeters(origin, it.latLng) / 1000.0)
            }
        } else {
            picker.pick(origin, radiusKm, all, excludePlaceIds)
        } ?: throw ApiException(ApiException.CODE_NO_CANDIDATE, "반경 ${radiusKm}km 안에 추천할 장소가 없어요")
        val tripId = "local_" + UUID.randomUUID().toString().take(6)
        trips[tripId] = FakeTrip(dest.latLng)
        return RandomDestinationResult(tripId, dest)
    }

    override suspend fun finishTrip(tripId: String, request: FinishTripRequest): RecapStatusResult {
        delay(latencyMs)
        if (request.points.size < 2) throw ApiException("INVALID_REQUEST", "points: size must be between 2 and 10000", 400)
        // 가짜 서버는 메모리 저장이라 앱 프로세스가 재시작되면 trip이 사라진다 → 앱이 발급한 local_ id는 다시 받아준다.
        val trip = trips[tripId]
            ?: if (tripId.startsWith("local_")) trips.getOrPut(tripId) { FakeTrip(request.points.last().latLng) }
            else throw ApiException("TRIP_NOT_FOUND", "trip not found: $tripId", 404)
        synchronized(trip) {
            if (!trip.finished) {
                trip.finished = true
                val pts = request.points
                val excluded = listOf(trip.destination) + request.events
                    .filter { it.type == TripEventType.WAYPOINT_ADDED || it.type == TripEventType.WAYPOINT_ARRIVED }
                    .map { LatLng(it.lat, it.lng) }
                trip.summary = RecapSummary(
                    distanceKm = Geo.pathLengthMeters(pts.map { it.latLng }) / 1000.0,
                    durationMin = ((request.endedAt - request.startedAt) / 60_000L).toInt(),
                    stopCount = StopDetector().detect(pts, excluded).size,
                )
            }
        }
        return RecapStatusResult(tripId, RecapStatus.PENDING)
    }

    override suspend fun getRecap(tripId: String): Recap {
        delay(latencyMs)
        // 앱 재시작으로 메모리 trip이 사라졌으면 404 → 앱이 finish를 다시 보낸다(실서버도 같은 경로로 복구됨).
        val trip = trips[tripId] ?: throw ApiException("TRIP_NOT_FOUND", "trip not found: $tripId", 404)
        synchronized(trip) {
            if (!trip.finished) return Recap(tripId, RecapStatus.PENDING, null, null, null)
            trip.polls++
            if (trip.polls <= 2) return Recap(tripId, RecapStatus.RUNNING, null, null, null)
            return Recap(tripId, RecapStatus.DONE, "https://gallaemallae.invalid/r/$tripId", null, trip.summary)
        }
    }

    /** 오프라인 개발용 가짜 서버는 지역 이름을 모른다(화면은 "현재 위치"). */
    override suspend fun areaName(lat: Double, lng: Double): AreaName? = null
}
