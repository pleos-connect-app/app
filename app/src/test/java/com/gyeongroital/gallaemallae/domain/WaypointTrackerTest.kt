package com.gyeongroital.gallaemallae.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaypointTrackerTest {
    private val mokdong = LatLng(37.5262, 126.8752)

    private fun at(p: LatLng, ts: Long) = TrackPoint(p.lat, p.lng, ts)

    @Test fun `경유지 반경 안에 10초 머물면 한 번만 도착`() {
        val t = WaypointTracker()
        t.add("목동", mokdong)
        assertTrue(t.onPoint(at(mokdong, 0)).isEmpty())
        assertEquals(listOf("목동" to mokdong), t.onPoint(at(mokdong, 10_000)))
        assertTrue(t.onPoint(at(mokdong, 20_000)).isEmpty())
    }

    @Test fun `좌표를 모르는 경유지는 도착 판정하지 않고 나중에 좌표를 알면 판정한다`() {
        val t = WaypointTracker()
        t.add("목동", null)
        assertTrue(t.onPoint(at(mokdong, 0)).isEmpty())
        assertTrue(t.onPoint(at(mokdong, 20_000)).isEmpty())
        t.add("목동", mokdong)
        t.onPoint(at(mokdong, 30_000))
        assertEquals(1, t.onPoint(at(mokdong, 40_000)).size)
    }

    @Test fun `내비 도착 알림이 먼저 오면 앱 판정은 건너뛴다`() {
        val t = WaypointTracker()
        t.add("목동", mokdong)
        assertTrue(t.markArrived("목동"))
        assertFalse(t.markArrived("목동"))
        t.onPoint(at(mokdong, 0))
        assertTrue(t.onPoint(at(mokdong, 10_000)).isEmpty())
    }

    @Test fun `저장된 이벤트로 복원 - 0,0은 좌표 모름`() {
        val t = WaypointTracker(listOf(
            TripEvent(TripEventType.WAYPOINT_ADDED, 1, 0.0, 0.0, "에뮬"),
            TripEvent(TripEventType.WAYPOINT_ADDED, 2, mokdong.lat, mokdong.lng, "목동"),
            TripEvent(TripEventType.WAYPOINT_ARRIVED, 3, mokdong.lat, mokdong.lng, "목동"),
        ))
        assertTrue(t.isKnown("에뮬"))
        assertNull(t.coordOf("에뮬"))
        assertEquals(mokdong, t.coordOf("목동"))
        assertFalse(t.markArrived("목동"))
    }

    @Test fun `0,0은 모르는 좌표`() {
        assertFalse(LatLng(0.0, 0.0).isKnown)
        assertTrue(mokdong.isKnown)
    }
}
