package com.gyeongroital.gallaemallae.domain

/**
 * 예상 밖 정차 후보 감지 (MVP F-05의 1차 규칙).
 *
 * - 단일 좌표로 판단하지 않고, [radiusMeters] 안에 머문 연속 점 묶음이 [minDurationMs] 이상일 때만 정차로 본다.
 * - 정차 전후에 주행 구간이 있어야 한다(출발 대기·도착 후 체류 제외).
 * - [excluded] 근처(목적지·계획 경유지)의 체류는 "예상 밖"이 아니므로 제외한다.
 *
 * 신호 대기(보통 1~3분 미만)와 구분하려고 기본 최소 체류를 5분으로 둔다. 기준값은 실제 주행 로그로 보정해야 한다.
 */
class StopDetector(
    private val radiusMeters: Double = 60.0,
    private val minDurationMs: Long = 5 * 60_000L,
    private val excludeRadiusMeters: Double = 300.0,
) {
    fun detect(points: List<TrackPoint>, excluded: List<LatLng> = emptyList()): List<Stop> {
        if (points.size < 3) return emptyList()
        val stops = mutableListOf<Stop>()
        var i = 0
        while (i < points.size) {
            val anchor = points[i]
            var j = i + 1
            while (j < points.size && Geo.distanceMeters(anchor.latLng, points[j].latLng) <= radiusMeters) j++
            val last = points[j - 1]
            val duration = last.ts - anchor.ts
            val hasDriveBefore = i > 0
            val hasDriveAfter = j < points.size
            if (duration >= minDurationMs) {
                // 긴 체류 묶음은 정차로 세든 안 세든 통째로 건너뛴다(묶음 중간점부터 다시 세면 출발 대기 후반부가 정차로 잡힘).
                if (hasDriveBefore && hasDriveAfter) {
                    val cluster = points.subList(i, j)
                    val center = LatLng(cluster.map { it.lat }.average(), cluster.map { it.lng }.average())
                    val nearExcluded = excluded.any { Geo.distanceMeters(it, center) <= excludeRadiusMeters }
                    if (!nearExcluded) stops += Stop(center.lat, center.lng, anchor.ts, last.ts)
                }
                i = j
            } else {
                i++
            }
        }
        return stops
    }
}
