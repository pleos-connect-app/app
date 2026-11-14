package com.gyeongroital.gallaemallae.data

import com.gyeongroital.gallaemallae.domain.RecapStatus
import com.gyeongroital.gallaemallae.domain.TrackPoint
import com.gyeongroital.gallaemallae.domain.TripEvent
import com.gyeongroital.gallaemallae.domain.TripEventType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** server/CLAUDE.md 계약 예시와 같은 JSON을 주고받는지 확인. */
class RecapJsonTest {
    @Test fun `랜덤 목적지 응답 파싱 - imageUrl null 허용`() {
        val body = """{"tripId":"t_722ecc","destination":{"placeId":"126508","name":"안목해변","lat":37.7718,"lng":128.9479,"address":"강원특별자치도 강릉시 창해로14번길","imageUrl":null,"distanceKm":92.4}}"""
        val r = RecapJson.parseRandomDestination(body)
        assertEquals("t_722ecc", r.tripId)
        assertEquals("안목해변", r.destination.name)
        assertNull(r.destination.imageUrl)
        assertEquals(92.4, r.destination.distanceKm, 1e-9)
    }

    @Test fun `finish 요청 - 이름 없는 이벤트는 name 생략`() {
        val json = JSONObject(RecapJson.finishRequest(FinishTripRequest(
            1L, 2L,
            listOf(TrackPoint(37.5, 127.0, 1L), TrackPoint(37.6, 127.1, 2L)),
            listOf(TripEvent(TripEventType.DESTINATION_SET, 1L, 37.5, 127.0, "남한산성"), TripEvent(TripEventType.DESTINATION_ARRIVED, 2L, 37.6, 127.1)),
        )))
        assertEquals(2, json.getJSONArray("points").length())
        assertEquals(1L, json.getJSONArray("points").getJSONObject(0).getLong("ts"))
        val events = json.getJSONArray("events")
        assertEquals("DESTINATION_SET", events.getJSONObject(0).getString("type"))
        assertEquals("남한산성", events.getJSONObject(0).getString("name"))
        assertFalse(events.getJSONObject(1).has("name"))
    }

    @Test fun `리캡 응답 - RUNNING이면 summary null`() {
        val r = RecapJson.parseRecap("""{"tripId":"t_1","status":"RUNNING","shareUrl":null,"videoUrl":null,"summary":null}""")
        assertEquals(RecapStatus.RUNNING, r.status)
        assertNull(r.summary)
        assertNull(r.shareUrl)
    }

    @Test fun `리캡 응답 - DONE`() {
        val r = RecapJson.parseRecap("""{"tripId":"t_1","status":"DONE","shareUrl":"http://x/r/demo123","videoUrl":"http://x/v.mp4","summary":{"distanceKm":214.6,"durationMin":150,"stopCount":2}}""")
        assertEquals(RecapStatus.DONE, r.status)
        assertEquals(2, r.summary!!.stopCount)
        assertEquals("http://x/r/demo123", r.shareUrl)
        assertNull(r.thumbnailUrl) // 필드가 없는 옛 응답도 그대로 읽는다
    }

    @Test fun `리캡 응답 - thumbnailUrl`() {
        val r = RecapJson.parseRecap("""{"tripId":"t_1","status":"DONE","shareUrl":"https://x/r/abc","videoUrl":null,"thumbnailUrl":"https://x/r/abc/thumbnail.jpg","summary":{"distanceKm":1.0,"durationMin":3,"stopCount":0}}""")
        assertEquals("https://x/r/abc/thumbnail.jpg", r.thumbnailUrl)
        assertNull(RecapJson.parseRecap("""{"tripId":"t_1","status":"DONE","thumbnailUrl":null}""").thumbnailUrl)
    }

    @Test fun `알 수 없는 상태값은 RUNNING으로 보고 계속 조회`() {
        assertEquals(RecapStatus.RUNNING, RecapJson.parseRecap("""{"tripId":"t","status":"QUEUED"}""").status)
    }

    @Test fun `오류 바디 파싱`() {
        assertEquals("TRIP_NOT_FOUND" to "trip not found: t_x", RecapJson.parseError("""{"code":"TRIP_NOT_FOUND","message":"trip not found: t_x"}"""))
        assertNull(RecapJson.parseError("<html>"))
    }
}
