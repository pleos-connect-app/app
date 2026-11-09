package ai.randomdrive.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    private val seoulStation = LatLng(37.5547, 126.9707)
    private val namhansanseong = LatLng(37.4786, 127.1817)

    @Test fun `서울역-남한산성 직선거리는 약 20km`() {
        assertEquals(20.4, Geo.distanceMeters(seoulStation, namhansanseong) / 1000, 0.5)
    }

    @Test fun `같은 점은 0m`() = assertEquals(0.0, Geo.distanceMeters(seoulStation, seoulStation), 1e-6)

    @Test fun `경로 길이는 구간 합`() {
        val mid = LatLng(37.52, 127.07)
        val expected = Geo.distanceMeters(seoulStation, mid) + Geo.distanceMeters(mid, namhansanseong)
        assertEquals(expected, Geo.pathLengthMeters(listOf(seoulStation, mid, namhansanseong)), 1e-6)
    }
}
