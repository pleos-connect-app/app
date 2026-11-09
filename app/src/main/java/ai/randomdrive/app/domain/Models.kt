package ai.randomdrive.app.domain

/** 추천 목적지. 서버 계약(DestinationDto)과 같은 필드. */
data class Destination(
    val placeId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String,
    val imageUrl: String?,
    /** 출발 위치 기준 직선거리(km). */
    val distanceKm: Double,
) {
    val latLng get() = LatLng(lat, lng)
}

/** GPS 기록 점. ts = epoch millis. */
data class TrackPoint(val lat: Double, val lng: Double, val ts: Long) {
    val latLng get() = LatLng(lat, lng)
}

/** 서버 계약의 events.type. */
enum class TripEventType { DESTINATION_SET, WAYPOINT_ADDED, WAYPOINT_ARRIVED, DESTINATION_ARRIVED }

/** 도착 판정 출처. 서버 계약에는 없고 앱 로그/보고용. */
enum class ArrivalSource { NAVI_EVENT, APP_GEOFENCE }

data class TripEvent(
    val type: TripEventType,
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val name: String? = null,
)

/** 정차(체류) 구간. */
data class Stop(val lat: Double, val lng: Double, val startTs: Long, val endTs: Long) {
    val durationMs get() = endTs - startTs
}

enum class RecapStatus { PENDING, RUNNING, DONE, FAILED }

data class RecapSummary(val distanceKm: Double, val durationMin: Int, val stopCount: Int)

data class Recap(
    val tripId: String,
    val status: RecapStatus,
    val shareUrl: String?,
    val videoUrl: String?,
    val summary: RecapSummary?,
)
