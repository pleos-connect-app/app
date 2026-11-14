package com.gyeongroital.gallaemallae.domain

/**
 * 앱 자체 도착 판정.
 * 에뮬레이터 내비는 가상 GPS를 인식하지 않아 onDestinationArrived가 오지 않는다(운영사무국 확인).
 * 그래서 앱이 받은 GPS가 목적지 반경 [radiusMeters] 안에 [dwellMillis] 이상 연속으로 머물면 도착으로 본다.
 * 한 번이라도 반경 밖 점이 들어오면 체류 시간을 다시 센다.
 */
class ArrivalDetector(
    private val destination: LatLng,
    private val radiusMeters: Double = DEFAULT_RADIUS_M,
    private val dwellMillis: Long = DEFAULT_DWELL_MS,
) {
    private var insideSince: Long? = null
    var arrived: Boolean = false
        private set

    /** 새 점을 넣고, 이번 점으로 처음 도착 판정이 났으면 true. */
    fun onPoint(point: TrackPoint): Boolean {
        if (arrived) return false
        val inside = Geo.distanceMeters(point.latLng, destination) <= radiusMeters
        if (!inside) {
            insideSince = null
            return false
        }
        val since = insideSince ?: point.ts.also { insideSince = it }
        if (point.ts - since >= dwellMillis) {
            arrived = true
            return true
        }
        return false
    }

    companion object {
        const val DEFAULT_RADIUS_M = 150.0
        const val DEFAULT_DWELL_MS = 10_000L
    }
}
