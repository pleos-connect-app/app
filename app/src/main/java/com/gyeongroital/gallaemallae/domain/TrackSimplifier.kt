package com.gyeongroital.gallaemallae.domain

/**
 * 업로드용 경로 점 줄이기. 서버 계약상 points는 2~10,000개.
 * 1) 직전에 남긴 점과 [minStepMeters] 미만으로 가까운 점은 버린다(정차 중 중복 제거).
 *    단, 정차 시간을 보존하도록 [maxGapMs] 이상 시간이 지난 점은 남긴다.
 * 2) 그래도 [maxPoints]를 넘으면 균등 간격으로 솎아낸다.
 * 첫 점과 마지막 점은 항상 유지한다.
 */
object TrackSimplifier {
    const val MAX_POINTS = 10_000

    fun simplify(
        points: List<TrackPoint>,
        minStepMeters: Double = 10.0,
        maxGapMs: Long = 30_000L,
        maxPoints: Int = MAX_POINTS,
    ): List<TrackPoint> {
        if (points.size <= 2) return points
        val kept = ArrayList<TrackPoint>(points.size)
        kept += points.first()
        for (k in 1 until points.lastIndex) {
            val p = points[k]
            val prev = kept.last()
            if (Geo.distanceMeters(prev.latLng, p.latLng) >= minStepMeters || p.ts - prev.ts >= maxGapMs) kept += p
        }
        kept += points.last()
        if (kept.size <= maxPoints) return kept
        val step = (kept.size - 1).toDouble() / (maxPoints - 1)
        return List(maxPoints) { idx -> kept[Math.round(idx * step).toInt().coerceAtMost(kept.lastIndex)] }
    }
}
