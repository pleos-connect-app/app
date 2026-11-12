package ai.randomdrive.app.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** 두 좌표 사이 직선(대원) 거리, 미터. */
    fun distanceMeters(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** [from]에서 [bearingDeg] 방향(북=0, 시계방향)으로 [distanceM]만큼 간 지점. */
    fun destination(from: LatLng, distanceM: Double, bearingDeg: Double): LatLng {
        val d = distanceM / EARTH_RADIUS_M
        val b = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(from.lat)
        val lng1 = Math.toRadians(from.lng)
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(b))
        val lng2 = lng1 + atan2(sin(b) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return LatLng(Math.toDegrees(lat2), Math.toDegrees(lng2))
    }

    /** 경로 점들을 이은 총 길이, 미터. */
    fun pathLengthMeters(points: List<LatLng>): Double =
        points.zipWithNext().sumOf { (a, b) -> distanceMeters(a, b) }
}
