package ai.randomdrive.app.trip

import ai.randomdrive.app.domain.ArrivalSource
import ai.randomdrive.app.domain.Destination
import ai.randomdrive.app.domain.LatLng
import ai.randomdrive.app.domain.RecapSummary
import ai.randomdrive.app.domain.TrackPoint
import ai.randomdrive.app.domain.TripEvent
import ai.randomdrive.app.domain.TripEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TripStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val session = TripSession(
        tripId = "local_abc123",
        destination = Destination("demo-namhansanseong", "남한산성", 37.4786, 127.1817, "경기도 광주시", null, 20.4),
        origin = LatLng(37.5547, 126.9707), originLabel = "현재 위치", radiusKm = 50,
        phase = TripPhase.RECORDING, startedAt = 1_000L,
    )

    @Test fun `프로세스 재시작 후 세션·점·이벤트 복원`() {
        val dir = tmp.newFolder()
        val store = TripStore(dir)
        store.start(session)
        repeat(25) { i -> store.appendPoint(TrackPoint(37.5 + i * 0.001, 127.0, 2_000L + i)) }
        store.addEvent(TripEvent(TripEventType.DESTINATION_SET, 1_500L, 37.5547, 126.9707, "남한산성"))
        store.update { it.copy(phase = TripPhase.ARRIVED, arrivalSource = ArrivalSource.APP_GEOFENCE, endedAt = 9_000L,
            localSummary = RecapSummary(20.1, 30, 1), thumbnailUrl = "https://x/r/abc/thumbnail.jpg") }

        val reopened = TripStore(dir)
        val s = reopened.session.value!!
        assertEquals(TripPhase.ARRIVED, s.phase)
        assertEquals(ArrivalSource.APP_GEOFENCE, s.arrivalSource)
        assertEquals(9_000L, s.endedAt)
        assertEquals(1, s.events.size)
        assertEquals("남한산성", s.events[0].name)
        assertEquals(RecapSummary(20.1, 30, 1), s.localSummary)
        assertEquals("https://x/r/abc/thumbnail.jpg", s.thumbnailUrl)
        assertEquals(25, reopened.points().size)
        assertEquals(2_024L, reopened.points().last().ts)
    }

    @Test fun `잘린 마지막 줄은 무시`() {
        val dir = tmp.newFolder()
        val store = TripStore(dir)
        store.start(session)
        store.appendPoint(TrackPoint(37.5, 127.0, 1L))
        java.io.File(dir, "trip/points.csv").appendText("2,37.6")
        assertEquals(1, TripStore(dir).points().size)
    }

    @Test fun `clear 후에는 세션 없음`() {
        val dir = tmp.newFolder()
        val store = TripStore(dir)
        store.start(session)
        store.clear()
        assertNull(TripStore(dir).session.value)
        assertEquals(0, TripStore(dir).points().size)
    }
}
