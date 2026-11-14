package com.gyeongroital.gallaemallae.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VWorldGeocoderTest {
    @Test
    fun parsesCityGuDong() {
        // 서울역 좌표 실제 응답(2026-11-12)에서 필요한 부분만
        val json = """{"response":{"status":"OK","result":[{"type":"parcel","text":"서울특별시 용산구 동자동 43-205",
            "structure":{"level0":"대한민국","level1":"서울특별시","level2":"용산구","level3":"","level4L":"동자동",
            "level4A":"회현동","level5":"43-205철","detail":""}}]}}"""
        assertEquals(AreaName("서울특별시 용산구 동자동", "용산구 동자동"), VWorldGeocoder.parse(json))
    }

    @Test
    fun cityWithDistrictKeepsBoth() {
        val json = """{"response":{"status":"OK","result":[{"structure":{"level1":"경기도","level2":"수원시 영통구","level3":"","level4L":"매탄동"}}]}}"""
        assertEquals(AreaName("경기도 수원시 영통구 매탄동", "수원시 영통구 매탄동"), VWorldGeocoder.parse(json))
    }

    @Test
    fun noResultIsNull() {
        assertNull(VWorldGeocoder.parse("""{"response":{"status":"NOT_FOUND"}}"""))
    }
}
