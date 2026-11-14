package com.gyeongroital.gallaemallae.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun `리캡 요약 거리는 서버 영상·썸네일과 같은 소수 첫째 자리`() {
        assertEquals("22.7km", formatSummaryKm(22.709958582588158))
        assertEquals("0.0km", formatSummaryKm(0.0))
        assertEquals("150.0km", formatSummaryKm(150.0))
    }

    @Test fun `대략적인 거리는 10km 이상이면 정수`() {
        assertEquals("9.5km", formatKm(9.5))
        assertEquals("14km", formatKm(14.3))
    }
}
