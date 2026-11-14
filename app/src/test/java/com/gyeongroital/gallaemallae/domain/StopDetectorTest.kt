package com.gyeongroital.gallaemallae.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StopDetectorTest {
    /** 북쪽으로 1초에 15m씩 이동하는 주행 구간. */
    private fun drive(fromLat: Double, startTs: Long, seconds: Int) =
        List(seconds) { i -> TrackPoint(fromLat + (i * 15.0) / 111_000.0, 127.0, startTs + i * 1_000L) }

    private fun stay(lat: Double, startTs: Long, seconds: Int, every: Int = 5) =
        List(seconds / every) { i -> TrackPoint(lat, 127.0, startTs + i * every * 1_000L) }

    @Test fun `주행-6분 정차-주행이면 정차 1회`() {
        val a = drive(37.0, 0, 120)
        val stopLat = a.last().lat
        val b = stay(stopLat, 121_000, 360)
        val c = drive(stopLat, 482_000, 120)
        val stops = StopDetector().detect(a + b + c)
        assertEquals(1, stops.size)
        assertEquals(stopLat, stops[0].lat, 1e-4)
    }

    @Test fun `2분 신호대기는 정차 아님`() {
        val a = drive(37.0, 0, 120)
        val b = stay(a.last().lat, 121_000, 120)
        val c = drive(a.last().lat, 242_000, 120)
        assertEquals(0, StopDetector().detect(a + b + c).size)
    }

    @Test fun `출발 전 대기와 도착 후 체류는 제외`() {
        val wait = stay(37.0, 0, 600)
        val a = drive(37.0, 601_000, 120)
        val after = stay(a.last().lat, 722_000, 600)
        assertEquals(0, StopDetector().detect(wait + a + after).size)
    }

    @Test fun `계획 경유지 근처 정차는 예상 밖이 아님`() {
        val a = drive(37.0, 0, 120)
        val stopLat = a.last().lat
        val b = stay(stopLat, 121_000, 360)
        val c = drive(stopLat, 482_000, 120)
        assertEquals(0, StopDetector().detect(a + b + c, excluded = listOf(LatLng(stopLat, 127.0))).size)
    }
}
