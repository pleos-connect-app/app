package ai.randomdrive.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSimplifierTest {
    @Test fun `정차 중 중복 점은 줄이되 30초 간격 점은 남김`() {
        val pts = List(120) { i -> TrackPoint(37.0, 127.0, i * 1_000L) } // 2분간 제자리
        val out = TrackSimplifier.simplify(pts)
        assertEquals(pts.first(), out.first())
        assertEquals(pts.last(), out.last())
        assertTrue("got ${out.size}", out.size in 4..7)
    }

    @Test fun `최대 개수를 넘으면 균등 솎기, 처음과 끝 유지`() {
        val pts = List(25_000) { i -> TrackPoint(37.0 + i * 0.0002, 127.0, i * 1_000L) } // 약 22m 간격
        val out = TrackSimplifier.simplify(pts)
        assertEquals(TrackSimplifier.MAX_POINTS, out.size)
        assertEquals(pts.first(), out.first())
        assertEquals(pts.last(), out.last())
        assertTrue(out.zipWithNext().all { (a, b) -> a.ts < b.ts })
    }

    @Test fun `2개 이하는 그대로`() {
        val pts = listOf(TrackPoint(1.0, 1.0, 0), TrackPoint(1.0, 1.0, 1))
        assertEquals(pts, TrackSimplifier.simplify(pts))
    }
}
