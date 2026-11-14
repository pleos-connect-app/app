package com.gyeongroital.gallaemallae.data

import com.gyeongroital.gallaemallae.domain.Candidate
import com.gyeongroital.gallaemallae.domain.RecapStatus
import com.gyeongroital.gallaemallae.domain.TrackPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeRecapApiTest {
    private val candidates = listOf(
        Candidate("demo-namhansanseong", "남한산성", 37.4786, 127.1817, "경기도 광주시"),
        Candidate("dumulmeori", "두물머리", 37.5432, 127.3186, "경기도 양평군"),
    )
    private val api = FakeRecapApi({ candidates }, latencyMs = 0)

    @Test fun `서버 mock과 같은 상태 흐름 - PENDING, RUNNING 2회, DONE`() = runTest {
        val r = api.randomDestination(37.5547, 126.9707, 50, demo = true)
        assertEquals("남한산성", r.destination.name)
        assertEquals(RecapStatus.PENDING, api.getRecap(r.tripId).status)
        val pts = listOf(TrackPoint(37.5547, 126.9707, 0), TrackPoint(37.4786, 127.1817, 1_800_000))
        assertEquals(RecapStatus.PENDING, api.finishTrip(r.tripId, FinishTripRequest(0, 1_800_000, pts, emptyList())).status)
        assertEquals(RecapStatus.RUNNING, api.getRecap(r.tripId).status)
        assertEquals(RecapStatus.RUNNING, api.getRecap(r.tripId).status)
        val done = api.getRecap(r.tripId)
        assertEquals(RecapStatus.DONE, done.status)
        assertNotNull(done.shareUrl)
        assertEquals(30, done.summary!!.durationMin)
        assertEquals(20.4, done.summary!!.distanceKm, 0.5)
    }

    @Test fun `허용되지 않은 반경은 INVALID_REQUEST`() = runTest {
        try {
            api.randomDestination(37.5, 127.0, 70, demo = false)
            fail()
        } catch (e: ApiException) {
            assertEquals("INVALID_REQUEST", e.code)
        }
    }

    @Test fun `없는 tripId는 TRIP_NOT_FOUND`() = runTest {
        try {
            api.getRecap("t_nope")
            fail()
        } catch (e: ApiException) {
            assertEquals("TRIP_NOT_FOUND", e.code)
            assertTrue(!e.retryable)
        }
    }

    @Test fun `반경 안 후보가 없으면 NO_CANDIDATE`() = runTest {
        try {
            api.randomDestination(33.4996, 126.5312, 50, demo = false) // 제주
            fail()
        } catch (e: ApiException) {
            assertEquals(ApiException.CODE_NO_CANDIDATE, e.code)
        }
    }

    @Test fun `앱 재시작(새 인스턴스) 후 finish를 다시 보내면 복구`() = runTest {
        val tripId = api.randomDestination(37.5547, 126.9707, 50, demo = true).tripId
        val restarted = FakeRecapApi({ candidates }, latencyMs = 0)
        try {
            restarted.getRecap(tripId)
            fail()
        } catch (e: ApiException) {
            assertEquals("TRIP_NOT_FOUND", e.code)
        }
        val pts = listOf(TrackPoint(37.5547, 126.9707, 0), TrackPoint(37.4786, 127.1817, 60_000))
        restarted.finishTrip(tripId, FinishTripRequest(0, 60_000, pts, emptyList()))
        repeat(2) { restarted.getRecap(tripId) }
        assertEquals(RecapStatus.DONE, restarted.getRecap(tripId).status)
    }
}
