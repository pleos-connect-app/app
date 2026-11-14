package com.gyeongroital.gallaemallae.domain

/**
 * 여행 중 경유지 상태(이름별 좌표·도착 여부)와 앱 자체 경유지 도착 판정.
 * - 내비 경유지 도착 알림(onWayPointArrived)은 에뮬레이터에서 오지 않으므로(내비 위치 고정),
 *   목적지와 같은 규칙([ArrivalDetector]: 반경 150m 안 10초)으로 앱이 직접 판정한다. 내비 알림이 먼저 오면 그걸 쓴다.
 * - 좌표를 모르는 경유지(에뮬레이터 내비는 0,0을 줌)는 기록은 하되 도착 판정을 하지 않는다.
 * - 서비스가 다시 시작돼도 이어지도록 저장된 이벤트로 상태를 복원한다.
 */
class WaypointTracker(events: List<TripEvent> = emptyList()) {

    private class Entry(val name: String, var at: LatLng?, var arrived: Boolean) {
        var detector: ArrivalDetector? = at?.let { ArrivalDetector(it) }
    }

    private val entries = LinkedHashMap<String, Entry>()

    init {
        events.forEach { e ->
            when (e.type) {
                TripEventType.WAYPOINT_ADDED -> add(e.name ?: UNNAMED, LatLng(e.lat, e.lng).takeIf { it.isKnown })
                TripEventType.WAYPOINT_ARRIVED -> {
                    val entry = entries.getOrPut(e.name ?: UNNAMED) { Entry(e.name ?: UNNAMED, null, false) }
                    entry.arrived = true
                }
                else -> Unit
            }
        }
    }

    fun isKnown(name: String): Boolean = entries.containsKey(name)

    fun coordOf(name: String): LatLng? = entries[name]?.at

    /** 새 경유지. 이미 있는 이름이면 좌표만 채운다(나중에 좌표를 알게 된 경우). */
    fun add(name: String, at: LatLng?) {
        val entry = entries[name]
        if (entry == null) {
            entries[name] = Entry(name, at, false)
        } else if (entry.at == null && at != null) {
            entry.at = at
            entry.detector = ArrivalDetector(at)
        }
    }

    /** 내비가 알려 준 도착. 처음이면 true(기록해야 함). */
    fun markArrived(name: String): Boolean {
        val entry = entries.getOrPut(name) { Entry(name, null, false) }
        if (entry.arrived) return false
        entry.arrived = true
        return true
    }

    /** GPS 점마다 호출. 이번 점으로 처음 도착 판정이 난 경유지들(이름, 좌표). */
    fun onPoint(p: TrackPoint): List<Pair<String, LatLng>> = entries.values
        .filter { !it.arrived && it.detector?.onPoint(p) == true }
        .map { it.arrived = true; it.name to it.at!! }

    companion object {
        const val UNNAMED = "경유지"

        /** 좌표를 모름을 뜻하는 값. 서버·앱 모두 이 좌표의 경유지는 그리거나 판정하지 않는다. */
        val UNKNOWN = LatLng(0.0, 0.0)
    }
}

/** 좌표를 아는지(0,0은 "모름"). */
val LatLng.isKnown: Boolean
    get() = !(kotlin.math.abs(lat) < 1e-6 && kotlin.math.abs(lng) < 1e-6) && lat in -90.0..90.0 && lng in -180.0..180.0
