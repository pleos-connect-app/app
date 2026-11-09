package ai.randomdrive.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalDetectorTest {
    private val dest = LatLng(37.4786, 127.1817)
    private fun at(latOffsetM: Double, ts: Long) = TrackPoint(dest.lat + latOffsetM / 111_000.0, dest.lng, ts)

    @Test fun `반경 안에 10초 머물면 도착`() {
        val d = ArrivalDetector(dest)
        assertFalse(d.onPoint(at(50.0, 0)))
        assertFalse(d.onPoint(at(40.0, 5_000)))
        assertTrue(d.onPoint(at(30.0, 10_000)))
        assertTrue(d.arrived)
        assertFalse("도착은 한 번만 보고", d.onPoint(at(30.0, 20_000)))
    }

    @Test fun `반경 밖으로 나가면 체류 시간 초기화`() {
        val d = ArrivalDetector(dest)
        d.onPoint(at(50.0, 0))
        d.onPoint(at(500.0, 6_000)) // 지나쳐 감
        assertFalse(d.onPoint(at(50.0, 12_000)))
        assertFalse(d.onPoint(at(50.0, 20_000)))
        assertTrue(d.onPoint(at(50.0, 22_000)))
    }

    @Test fun `반경 밖이면 오래 있어도 도착 아님`() {
        val d = ArrivalDetector(dest)
        for (t in 0..60) assertFalse(d.onPoint(at(400.0, t * 1_000L)))
    }
}
