package com.gyeongroital.gallaemallae.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CandidatePickerTest {
    private val origin = LatLng(37.5547, 126.9707) // 서울역
    private val all = listOf(
        Candidate("near", "너무 가까움", 37.5560, 126.9720, ""),      // ~0.2km
        Candidate("a", "남한산성", 37.4786, 127.1817, ""),           // ~20km
        Candidate("b", "두물머리", 37.5432, 127.3186, ""),           // ~31km
        Candidate("far", "공주 공산성", 36.4627, 127.1253, ""),      // ~122km
    )

    @Test fun `50km는 반경 안이면서 최소거리 이상인 후보만`() {
        val ids = CandidatePicker().candidatesWithin(origin, 50, all).map { it.first.placeId }.toSet()
        assertEquals(setOf("a", "b"), ids)
    }

    @Test fun `150km면 먼 후보 포함`() {
        assertTrue(CandidatePicker().candidatesWithin(origin, 150, all).any { it.first.placeId == "far" })
    }

    @Test fun `다시 뽑기는 이미 본 후보를 제외`() {
        repeat(20) { seed ->
            val d = CandidatePicker(Random(seed)).pick(origin, 50, all, excludeIds = setOf("a"))
            assertEquals("b", d?.placeId)
        }
    }

    @Test fun `모두 봤으면 전체에서 다시 뽑음`() {
        val d = CandidatePicker(Random(1)).pick(origin, 50, all, excludeIds = setOf("a", "b"))
        assertNotEquals(null, d)
    }

    @Test fun `후보가 없으면 null`() = assertNull(CandidatePicker().pick(LatLng(33.0, 126.0), 50, all))

    @Test fun `결과에 직선거리 km 포함`() {
        val d = CandidatePicker(Random(0)).pick(origin, 50, all, excludeIds = setOf("b"))!!
        assertEquals(20.4, d.distanceKm, 0.5)
    }
}
